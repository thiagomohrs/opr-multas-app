package com.opr.multas.controller;

import com.opr.multas.hateoas.LinkFacade;
import com.opr.multas.model.HistoricoScore;
import com.opr.multas.model.Usuario;
import com.opr.multas.model.VotoRevisao;
import com.opr.multas.repository.HistoricoScoreRepository;
import com.opr.multas.repository.VotoRevisaoRepository;
import com.opr.multas.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/meu-score")
@RequiredArgsConstructor
public class ScoreController {

    private final HistoricoScoreRepository historicoScoreRepository;
    private final VotoRevisaoRepository votoRepository;
    private final UsuarioService usuarioService;
    private final LinkFacade linkFacade;

    @GetMapping
    public String meuScore(Model model, Authentication authentication) {
        Usuario usuario = usuarioService.getCurrentUsuario(authentication);
        if (usuario == null) {
            return "redirect:/login";
        }

        List<HistoricoScore> historico = historicoScoreRepository.findByUsuarioOrderByRegistradoEmDesc(usuario);
        List<VotoRevisao> votos = votoRepository.findByRevisorOrderByVotadoEmDesc(usuario);

        model.addAttribute("usuario", usuario);
        model.addAttribute("historico", historico);
        model.addAttribute("votos", votos);
        model.addAttribute("links", linkFacade.meuScore());
        return "usuario/score";
    }
}
