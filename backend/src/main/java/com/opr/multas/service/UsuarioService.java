package com.opr.multas.service;

import com.opr.multas.model.Usuario;
import com.opr.multas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public Usuario getCurrentUsuario(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return usuarioRepository.findByEmail(name)
            .or(() -> usuarioRepository.findByLogin(name))
            .orElse(null);
    }
}
