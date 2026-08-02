package com.opr.multas.service;

import com.opr.multas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return usuarioRepository.findByLogin(login)
            .map(usuario -> {
                log.debug("Usuário encontrado: {}", usuario.getLogin());
                return new User(
                    usuario.getLogin(),
                    usuario.getSenha(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRole()))
                );
            })
            .orElseThrow(() -> {
                log.warn("Usuário não encontrado: {}", login);
                return new UsernameNotFoundException("Usuário não encontrado: " + login);
            });
    }
}
