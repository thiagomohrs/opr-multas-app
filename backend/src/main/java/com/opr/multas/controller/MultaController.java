package com.opr.multas.controller;

import com.opr.multas.model.AnexoMulta;
import com.opr.multas.model.Multa;
import com.opr.multas.model.TipoInfracao;
import com.opr.multas.model.Usuario;
import com.opr.multas.model.dto.MultaDto;
import com.opr.multas.service.MultaService;
import com.opr.multas.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/multas")
@RequiredArgsConstructor
public class MultaController {

    private final MultaService multaService;
    private final UsuarioService usuarioService;

    @GetMapping
    public String listar(@RequestParam(required = false) String placa, Model model,
                         Authentication authentication) {
        Usuario currentUser = usuarioService.getCurrentUsuario(authentication);
        boolean isAdmin = currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean temBusca = placa != null && !placa.isBlank();

        List<MultaDto> multas;
        if (isAdmin) {
            // ADMIN enxerga todos os casos.
            multas = temBusca
                ? multaService.listarPorPlaca(placa)
                : multaService.listarTodas();
        } else if (currentUser != null) {
            // Usuário comum: apenas os casos que ele mesmo criou.
            multas = temBusca
                ? multaService.listarPorUsuarioEPlaca(currentUser.getId(), placa)
                : multaService.listarPorUsuario(currentUser.getId());
        } else {
            multas = List.of();
        }

        model.addAttribute("multas", multas);
        model.addAttribute("searchPlaca", placa);
        model.addAttribute("statusValues", Multa.StatusMulta.values());
        model.addAttribute("stats", multaService.contarPorStatusModeracao());
        model.addAttribute("isAdmin", isAdmin);
        return "multas/list";
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("multa", multaService.buscarEntidadePorId(id));
        return "multas/detalhe";
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("multa", new Multa());
        model.addAttribute("tipos", TipoInfracao.TODOS);
        model.addAttribute("statusValues", Multa.StatusMulta.values());
        model.addAttribute("isEdit", false);
        return "multas/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute Multa multa, BindingResult result,
                        @RequestParam(value = "arquivos", required = false) MultipartFile[] arquivos,
                        Model model, Authentication authentication, RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("tipos", TipoInfracao.TODOS);
            model.addAttribute("statusValues", Multa.StatusMulta.values());
            model.addAttribute("isEdit", false);
            return "multas/form";
        }
        try {
            multaService.criar(multa, usuarioService.getCurrentUsuario(authentication), arquivos);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("tipos", TipoInfracao.TODOS);
            model.addAttribute("statusValues", Multa.StatusMulta.values());
            model.addAttribute("isEdit", false);
            return "multas/form";
        }
        redirectAttrs.addFlashAttribute("successMessage", "Multa criada com sucesso!");
        return "redirect:/multas";
    }

    @GetMapping("/{id}/editar")
    @PreAuthorize("hasRole('ADMIN')")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("multa", multaService.buscarEntidadePorId(id));
        model.addAttribute("tipos", TipoInfracao.TODOS);
        model.addAttribute("statusValues", Multa.StatusMulta.values());
        model.addAttribute("isEdit", true);
        return "multas/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String atualizar(@PathVariable Long id, @Valid @ModelAttribute Multa multa,
                            BindingResult result,
                            @RequestParam(value = "arquivos", required = false) MultipartFile[] arquivos,
                            Model model, RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            multa.setAnexos(multaService.buscarEntidadePorId(id).getAnexos());
            model.addAttribute("tipos", TipoInfracao.TODOS);
            model.addAttribute("statusValues", Multa.StatusMulta.values());
            model.addAttribute("isEdit", true);
            return "multas/form";
        }
        try {
            multaService.atualizar(id, multa, arquivos);
        } catch (IllegalArgumentException ex) {
            multa.setAnexos(multaService.buscarEntidadePorId(id).getAnexos());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("tipos", TipoInfracao.TODOS);
            model.addAttribute("statusValues", Multa.StatusMulta.values());
            model.addAttribute("isEdit", true);
            return "multas/form";
        }
        redirectAttrs.addFlashAttribute("successMessage", "Multa atualizada com sucesso!");
        return "redirect:/multas";
    }

    @PostMapping("/{id}/deletar")
    @PreAuthorize("hasRole('ADMIN')")
    public String deletar(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        multaService.deletar(id);
        redirectAttrs.addFlashAttribute("successMessage", "Multa removida com sucesso!");
        return "redirect:/multas";
    }

    @PostMapping("/{id}/anexos/{anexoId}/deletar")
    @PreAuthorize("hasRole('ADMIN')")
    public String deletarAnexo(@PathVariable Long id, @PathVariable Long anexoId, RedirectAttributes redirectAttrs) {
        try {
            multaService.removerAnexo(id, anexoId);
            redirectAttrs.addFlashAttribute("successMessage", "Anexo removido com sucesso!");
        } catch (IllegalArgumentException ex) {
            redirectAttrs.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/multas/" + id;
    }

    @GetMapping("/{id}/anexos/{anexoId}")
    public ResponseEntity<byte[]> anexo(@PathVariable Long id, @PathVariable Long anexoId) {
        try {
            AnexoMulta anexo = multaService.buscarAnexo(id, anexoId);
            byte[] conteudo = multaService.lerConteudoAnexo(id, anexoId);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anexo.getContentType()))
                .contentLength(conteudo != null ? conteudo.length : 0)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(conteudo);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
