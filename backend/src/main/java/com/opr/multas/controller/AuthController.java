package com.opr.multas.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @GetMapping("/")
    public String root() {
        return "redirect:/multas";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        // Só exibe "Continuar com o Google" se um client real estiver configurado
        // (evita o erro 401 invalid_client quando os placeholders estão em uso).
        model.addAttribute("googleOAuthEnabled",
            googleClientId != null
                && !googleClientId.isBlank()
                && !googleClientId.startsWith("SUBSTITUA_"));
        return "login";
    }
}
