package com.opr.multas.controller;

import com.opr.multas.model.Multa;
import com.opr.multas.model.VotoRevisao;
import com.opr.multas.repository.VotoRevisaoRepository;
import com.opr.multas.service.ModeracaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final ModeracaoService moderacaoService;
    private final VotoRevisaoRepository votoRepository;

    @GetMapping("/moderacao")
    public String dashboard(Model model) {
        model.addAttribute("casos", moderacaoService.listarTodosCasos());
        return "admin/moderacao";
    }

    @GetMapping("/moderacao/{id}")
    public String detalheCaso(@PathVariable Long id, Model model) {
        Multa multa = moderacaoService.buscarCasoPorId(id);
        model.addAttribute("multa", multa);
        model.addAttribute("votos", votoRepository.findByMultaOrderByVotadoEmAsc(multa));
        return "admin/caso";
    }

    @PostMapping("/moderacao/{id}/flag")
    public String flagMalicioso(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            moderacaoService.flagCasoMalicioso(id);
            redirectAttrs.addFlashAttribute("successMessage", "Caso marcado como malicioso e revisores penalizados.");
        } catch (RuntimeException ex) {
            redirectAttrs.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/moderacao";
    }
}
