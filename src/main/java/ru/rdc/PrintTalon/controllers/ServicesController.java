package ru.rdc.PrintTalon.controllers;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import ru.rdc.PrintTalon.dto.ServiceDTO;
import ru.rdc.PrintTalon.model.Service;
import ru.rdc.PrintTalon.repository.ServicesRepository;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ServicesController {

    private final ServicesRepository servicesRepository;

    @GetMapping("/services")
    public String showServices(HttpSession session, Model model) {

        if (session.getAttribute("patientId") == null) {
            return "redirect:/login?action=talon";
        }

        Long patientId = (Long) session.getAttribute("patientId");

        model.addAttribute("patientName", session.getAttribute("patientName"));

        LocalDate from = LocalDate.now().minusMonths(2).withDayOfMonth(1); // дата за 2 месяца назад, первое число того месяца
        LocalDate to = LocalDate.of(2100, 12, 31); // конечная дата - 31 декабря 2100 года

        List<ServiceDTO> services = servicesRepository.getServicesForPatient(patientId, from, to);
        services.sort(Comparator.comparing(ServiceDTO::getDat).reversed());

        model.addAttribute("services", services);
        //model.addAttribute("defaultFromDate", from.toString()); // передаем в HTML
        //model.addAttribute("currentDate", to);      // передаем текущую дату как maxDate
        model.addAttribute("defaultFromDate", from.format(DateTimeFormatter.ISO_DATE)); // "2025-04-01"
        model.addAttribute("currentDate", to.format(DateTimeFormatter.ISO_DATE));      // "2025-06-16"

        return "services";
    }

    @GetMapping("/services/ajax")
    @ResponseBody
    public List<Map<String, String>> getServicesAjax(@RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate, HttpSession session) {
        Long patientId = (Long) session.getAttribute("patientId");
        if (patientId == null) return Collections.emptyList();

        LocalDate to = LocalDate.of(2100, 12, 31); // конечная дата - 31 декабря 2100 года
        List<ServiceDTO> services = servicesRepository.getServicesForPatient(patientId, fromDate, to);
        services.sort(Comparator.comparing(ServiceDTO::getDat).reversed());

        // Используем ISO формат для надежного парсинга в JS
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        // Преобразуем в простой список для JSON
        return services.stream().map(s -> {
            Map<String, String> map = new HashMap<>();
            map.put("room", s.getRoom());
            map.put("sText", s.getSText());
            map.put("dat", s.getDat().format(formatter));
            map.put("doctor", s.getDoctor());
            map.put("dirOrg", s.getDirOrg());
            map.put("keyid", s.getKeyid().toString());
            return map;
        }).collect(Collectors.toList());
    }

}