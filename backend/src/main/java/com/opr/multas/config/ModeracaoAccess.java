package com.opr.multas.config;

import com.opr.multas.model.Usuario;
import com.opr.multas.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("moderacaoAccess")
@RequiredArgsConstructor
public class ModeracaoAccess {

    private final UsuarioService usuarioService;

    public boolean isRevisor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = usuarioService.getCurrentUsuario(authentication);
        return usuario != null && Boolean.TRUE.equals(usuario.getIsRevisor());
    }
}
