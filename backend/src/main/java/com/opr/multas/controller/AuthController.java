package com.opr.multas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/")
    public String root() {
        return "redirect:/multas";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
