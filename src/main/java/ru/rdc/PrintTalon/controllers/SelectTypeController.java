package ru.rdc.PrintTalon.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SelectTypeController {

    @GetMapping("/select-type")
    public String showSelectTypePage(HttpSession session, Model model) {
        // Проверка: если пользователь не авторизован, вернуть на login
        if (session.getAttribute("patientId") == null) {
            return "redirect:/login";
        }

        // Можно передать имя пациента, если нужно показать приветствие
        model.addAttribute("patientName", session.getAttribute("patientName"));

        return "selecttype";  // возвращаем шаблон selecttype.html
    }
}