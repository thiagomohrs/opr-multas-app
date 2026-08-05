package com.opr.multas.controller;

import com.opr.multas.config.ModeracaoProperties;
import com.opr.multas.model.DecisaoVoto;
import com.opr.multas.model.Multa;
import com.opr.multas.model.Usuario;
import com.opr.multas.model.VotoRevisao;
import com.opr.multas.repository.VotoRevisaoRepository;
import com.opr.multas.service.FilaRevisaoService;
import com.opr.multas.service.ModeracaoService;
import com.opr.multas.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/revisao")
@RequiredArgsConstructor
public class RevisaoController {

    private final ModeracaoService moderacaoService;
    private final VotoRevisaoRepository votoRepository;
    private final UsuarioService usuarioService;
    private final ModeracaoProperties props;
    private final FilaRevisaoService filaRevisaoService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @moderacaoAccess.isRevisor()")
    public String fila(Model model, Authentication authentication) {
        Usuario revisor = usuarioService.getCurrentUsuario(authentication);
        if (revisor != null) {
            Long proximo = filaRevisaoService.proximoCaso(revisor);
            if (proximo != null) {
                return "redirect:/revisao/" + proximo;
            }
        }
        // Sem casos na fila (ou sem usuário identificado): apenas a visão do administrador.
        model.addAttribute("filaVazia", true);
        model.addAttribute("fila", moderacaoService.listarFilaRevisao());
        model.addAttribute("resolvidos", moderacaoService.listarCasosResolvidos());
        return "revisao/fila";
    }

    @PostMapping("/proximo")
    @PreAuthorize("hasRole('ADMIN') or @moderacaoAccess.isRevisor()")
    public String proximo(Authentication authentication, RedirectAttributes redirectAttrs) {
        Usuario revisor = usuarioService.getCurrentUsuario(authentication);
        if (revisor == null) {
            return "redirect:/login";
        }
        Long proximo = filaRevisaoService.proximoCaso(revisor);
        if (proximo == null) {
            redirectAttrs.addFlashAttribute("infoMessage", "Nenhum caso disponível para revisão no momento.");
            return "redirect:/revisao";
        }
        return "redirect:/revisao/" + proximo;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @moderacaoAccess.isRevisor()")
    public String detalhe(@PathVariable Long id, Model model, Authentication authentication) {
        Multa multa = moderacaoService.buscarCasoPorId(id);
        Usuario revisor = usuarioService.getCurrentUsuario(authentication);
        VotoRevisao meuVoto = revisor != null
            ? votoRepository.findByRevisorAndMulta(revisor, multa).orElse(null)
            : null;

        model.addAttribute("multa", multa);
        model.addAttribute("votos", votoRepository.findByMultaOrderByVotadoEmAsc(multa));
        model.addAttribute("meuVoto", meuVoto);
        model.addAttribute("anexos", multa.getAnexos());
        model.addAttribute("decisoes", DecisaoVoto.values());
        model.addAttribute("percentFavor", calcularPercentFavor(multa));
        if (revisor != null && revisor.getScore() != null) {
            model.addAttribute("pesoVoto", revisor.getScore() / (double) props.getModeracao().getLimiarRevisor());
        } else {
            model.addAttribute("pesoVoto", 0.0);
        }
        return "revisao/detalhe";
    }

    @PostMapping("/{id}/votar")
    @PreAuthorize("hasRole('ADMIN') or @moderacaoAccess.isRevisor()")
    public String votar(@PathVariable Long id, @RequestParam DecisaoVoto decisao,
                        Authentication authentication, RedirectAttributes redirectAttrs) {
        Usuario revisor = usuarioService.getCurrentUsuario(authentication);
        if (revisor == null) {
            redirectAttrs.addFlashAttribute("errorMessage", "Usuário não identificado.");
            return "redirect:/login";
        }
        try {
            moderacaoService.registrarVoto(id, revisor, decisao);
            filaRevisaoService.devolverSeAberto(id);
            redirectAttrs.addFlashAttribute("successMessage", "Voto registrado com sucesso!");
        } catch (RuntimeException ex) {
            redirectAttrs.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/revisao";
    }

    private double calcularPercentFavor(Multa multa) {
        double total = multa.getPesoVotosAFavor() + multa.getPesoVotosContra();
        return total == 0 ? 0 : multa.getPesoVotosAFavor() / total * 100;
    }
}
