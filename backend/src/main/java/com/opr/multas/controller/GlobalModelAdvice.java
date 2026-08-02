package com.opr.multas.controller;

import com.opr.multas.config.ModeracaoProperties;
import com.opr.multas.model.Usuario;
import com.opr.multas.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final UsuarioService usuarioService;
    private final ModeracaoProperties props;

    @ModelAttribute
    public void addGlobalAttributes(Model model, Authentication authentication) {
        Usuario currentUser = usuarioService.getCurrentUsuario(authentication);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("limiarRevisor", props.getModeracao().getLimiarRevisor());
        model.addAttribute("votosNecessarios", props.getModeracao().getVotosNecessarios());
    }
}
