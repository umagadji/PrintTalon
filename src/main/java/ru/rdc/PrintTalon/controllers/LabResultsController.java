package ru.rdc.PrintTalon.controllers;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import ru.rdc.PrintTalon.dto.LabResultViewDto;
import ru.rdc.PrintTalon.dto.LabTestResultDto;
import ru.rdc.PrintTalon.model.Patient;
import ru.rdc.PrintTalon.repository.PatientRepository;
import ru.rdc.PrintTalon.services.LabResultService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LabResultsController {

    private final LabResultService labResultService;
    private final PatientRepository patientRepository;

    @GetMapping("/labresults")
    public String getLabResultsPage(HttpSession session, Model model) {

        if (session.getAttribute("patientId") == null) {
            return "redirect:/login?action=results";
        }

        Long patientId = (Long) session.getAttribute("patientId");

        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) return "redirect:/login";

        Patient patient = patientOpt.get();

        // Добавляем расчет даты за три недели назад
        LocalDate threeWeeksAgo = LocalDate.now().minusWeeks(3);
        LocalDate currentDate = LocalDate.now();

        model.addAttribute("patientName", session.getAttribute("patientName"));
        model.addAttribute("patientSnils", patient.getSnils());
        model.addAttribute("patientInfo", new Object()); // можно сделать заглушку LabTestResultDto

        // Передаем даты в формате ISO (YYYY-MM-DD)
        model.addAttribute("defaultFromDate", threeWeeksAgo.format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("currentDate", currentDate.format(DateTimeFormatter.ISO_DATE));

        return "labresults";
    }

    /**
     * Обрабатывает AJAX-запрос для получения лабораторных результатов по СНИЛС и дате.
     * Возвращает данные, сгруппированные по категориям, в формате JSON.
     *
     * @param date    Дата анализа
     * @param session HTTP-сессия (не используется, но может быть полезна для аутентификации)
     * @return        JSON-ответ с результатами по категориям: afp_hgch, vpch, regular и др.
     */
    @GetMapping("/labresults/ajax")
    @ResponseBody
    public Map<String, List<Map<String, String>>> getLabResultsAjax(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpSession session) {

        Long patientId = (Long) session.getAttribute("patientId");

        System.out.println("patientId = " + patientId);

        // Получаем результаты анализов, сгруппированные по категориям (внутри сервиса)
        Map<String, List<LabResultViewDto>> categorized = labResultService.getCategorizedResultsForView(patientId, date);

        // Подготовим результат для клиента: каждая категория будет содержать список упрощённых DTO
        Map<String, List<Map<String, String>>> response = new LinkedHashMap<>();

        // Перебираем каждую категорию (например, "vpch", "afp_hgch", "regular")
        categorized.forEach((category, list) -> {
            // Преобразуем каждый DTO в Map<String, String>, т.к. клиенту нужны строки
            List<Map<String, String>> mappedList = list.stream().map(r -> {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("ids", r.getIds());                    // ID образца
                map.put("material", r.getMaterial());          // Материал исследования
                map.put("otd", r.getOtd());                    // Отделение
                map.put("usl", r.getUsl());                    // Услуга
                map.put("collecdate", r.getCollecdate().toString()); // Дата забора
                map.put("finisdate", r.getFinisdate().toString());   // Дата готовности
                return map;
            }).toList();

            // Добавляем полученный список в ответ
            response.put(category, mappedList);
        });

        // Возвращаем сформированный JSON (Spring сам сериализует Map в JSON)
        return response;
    }

    /**
     * Обрабатывает запрос на печать результатов лабораторных исследований.
     * Формирует PDF-документ по СНИЛС, дате и идентификатору образца (ids).
     *
     * @param snils     СНИЛС пациента
     * @param date      Дата анализа
     * @param ids       Идентификатор образца (результата)
     * @param response  HTTP-ответ, в который будет записан PDF-файл
     * @throws IOException В случае ошибок при записи в поток ответа
     */
    @GetMapping("/labresults/print")
    public void printLabResults(
            @RequestParam String snils,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String ids,
            HttpSession session,
            HttpServletResponse response) throws IOException {

        Long patientId = (Long) session.getAttribute("patientId");

        // Получаем список результатов по СНИЛС и дате, фильтруем по ids (идентификатору образца)
        List<LabTestResultDto> results = labResultService.findResultsByPatientIdAndDate(patientId, date)
                .stream()
                .filter(r -> r.getIds().equals(ids)) // фильтрация по конкретному образцу
                .collect(Collectors.toList());

        // Логируем информацию о запросе на печать
        log.info("🖨️ Печать результата: СНИЛС={}, дата={}, IDS={}, найдено записей={}",
                snils, date, ids, results.size());

        // Устанавливаем тип содержимого — PDF-документ
        response.setContentType("application/pdf");

        // Указываем браузеру отображать PDF внутри вкладки, а не скачивать его
        response.setHeader("Content-Disposition", "inline; filename=lab_results_" + ids + ".pdf");
    }
}