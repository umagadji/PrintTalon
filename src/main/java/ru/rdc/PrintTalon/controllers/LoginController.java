package ru.rdc.PrintTalon.controllers;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rdc.PrintTalon.model.Patient;
import ru.rdc.PrintTalon.repository.PatientRepository;

import java.util.Optional;

@Controller
public class LoginController {

    @Autowired
    private PatientRepository patientRepository;

    @GetMapping("/")
    public String showIndex() {
        return "redirect:/index";
    }

    @GetMapping("/index")
    public String showIndexPage() {
        return "index";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam String credential,
                              @RequestParam String authMethod,
                              @RequestParam String action,
                              HttpSession session,
                              Model model) {

        Optional<Patient> patientOpt = Optional.empty();
        String errorMessage = "";

        // Проверка на пустые данные
        if (credential == null || credential.trim().isEmpty()) {
            switch (authMethod) {
                case "snils":
                    errorMessage = "Введите СНИЛС";
                    break;
                case "policy":
                    errorMessage = "Введите номер полиса";
                    break;
                case "card":
                    errorMessage = "Введите номер карты";
                    break;
                default:
                    errorMessage = "Введите данные для авторизации";
            }
            model.addAttribute("error", errorMessage);
            model.addAttribute("action", action);
            return "login";
        }

        // Проверка длины введенных данных
        switch (authMethod) {
            case "snils":
                if (credential.length() != 11) {
                    errorMessage = "СНИЛС должен содержать 11 цифр";
                } else {
                    patientOpt = patientRepository.authenticateBySnils(credential);
                    errorMessage = "Пациент с таким СНИЛС не найден";
                }
                break;
            case "policy":
                if (credential.length() != 16) {
                    errorMessage = "Номер полиса должен содержать 16 цифр";
                } else {
                    patientOpt = patientRepository.authenticateByPolicyNumber(credential);
                    errorMessage = "Пациент с таким номером полиса не найден";
                }
                break;
            case "card":
                if (credential.length() < 1 || credential.length() > 7) {
                    errorMessage = "Номер карты должен содержать от 1 до 7 цифр";
                } else {
                    patientOpt = patientRepository.authenticateByCardNumber(credential);
                    errorMessage = "Пациент с таким номером карты не найден";
                }
                break;
            default:
                errorMessage = "Неизвестный метод аутентификации";
        }

        // Если есть ошибка валидации
        if (!errorMessage.isEmpty() && !patientOpt.isPresent()) {
            model.addAttribute("error", errorMessage);
            model.addAttribute("action", action);
            return "login";
        }

        // Если пациент найден
        Patient patient = patientOpt.get();
        session.setAttribute("patientId", patient.getPatientId());
        session.setAttribute("patientName", patient.getName());

        return "redirect:/" + ("results".equals(action) ? "labresults" : "services");
    }

    @GetMapping("/login")
    public String showLoginPage(@RequestParam(required = false) String action,
                                HttpSession session, Model model) {
        // Если уже авторизован, перенаправляем на соответствующую страницу
        if (session.getAttribute("patientId") != null) {
            return "redirect:/" + ("results".equals(action) ? "labresults" : "services");
        }

        model.addAttribute("action", action); // передаем действие в шаблон
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse response) {
        session.invalidate();

        // Добавляем заголовок для очистки localStorage
        response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\", \"executionContexts\"");

        return "redirect:/";
    }
}