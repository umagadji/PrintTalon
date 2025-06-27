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

    @PostMapping("/login")
    public String handleLogin(@RequestParam String snils,
                              HttpSession session,
                              Model model) {

        Optional<Patient> patientOpt = patientRepository.authenticate(snils);

        if (patientOpt.isPresent()) {
            Patient patient = patientOpt.get();
            session.setAttribute("patientId", patient.getPatientId());
            session.setAttribute("patientName", patient.getName());
            return "redirect:/select-type";
        } else {
            model.addAttribute("error", "Пациент с таким СНИЛС не найден в системе");
            return "login";
        }
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse response) {
        session.invalidate();

        // Добавляем заголовок для очистки localStorage
        response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\", \"executionContexts\"");

        return "redirect:/login";
    }

    /*@GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }*/
}