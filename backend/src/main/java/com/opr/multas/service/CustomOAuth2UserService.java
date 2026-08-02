package com.opr.multas.service;

import com.opr.multas.model.ProvedorAuth;
import com.opr.multas.model.Usuario;
import com.opr.multas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        String sub = oauthUser.getAttribute("sub");
        String nome = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");

        Usuario usuario = encontrarOuCriarUsuario(email, sub, nome, picture);

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("usuario", usuario);

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRole())),
            attributes,
            "email"
        );
    }

    private Usuario encontrarOuCriarUsuario(String email, String sub, String nome, String picture) {
        if (email == null) {
            throw new OAuth2AuthenticationException("E-mail obrigatório para autenticação via Google.");
        }

        return usuarioRepository.findByGoogleSub(sub)
            .or(() -> usuarioRepository.findByEmail(email))
            .map(usuario -> {
                log.info("Linkando conta Google ao usuário '{}'", usuario.getLogin());
                usuario.setGoogleSub(sub);
                usuario.setProvider(ProvedorAuth.GOOGLE);
                usuario.setAvatarUrl(picture);
                return usuarioRepository.save(usuario);
            })
            .orElseGet(() -> {
                log.info("Criando usuário via Google: {}", email);
                Usuario usuario = new Usuario();
                usuario.setLogin(email);
                usuario.setEmail(email);
                usuario.setNome(nome != null ? nome : email);
                usuario.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
                usuario.setRole("USER");
                usuario.setProvider(ProvedorAuth.GOOGLE);
                usuario.setGoogleSub(sub);
                usuario.setAvatarUrl(picture);
                usuario.setScore(0);
                usuario.setIsRevisor(false);
                return usuarioRepository.save(usuario);
            });
    }
}
