package com.opr.multas.hateoas;

import com.opr.multas.controller.AdminController;
import com.opr.multas.controller.MultaController;
import com.opr.multas.controller.RevisaoController;
import com.opr.multas.controller.ScoreController;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

/**
 * Fachada de hipermídia (HATEOAS): monta os {@code Link} (recursos/href) que tornam
 * os recursos da aplicação navegáveis. Os links são derivados do mapeamento real dos
 * controllers via {@code linkTo(Class)}, evitando URLs codificadas. Não usa
 * {@code methodOn(...)} porque os handlers MVC retornam {@code String} (nome da view),
 * um tipo final que não pode ser proxyado por CGLIB.
 */
@Component
public class LinkFacade {

    /** Coleção principal de recursos de multas (self). */
    public List<Link> multas() {
        return List.of(linkTo(MultaController.class).withSelfRel());
    }

    /** Links do recurso multa (self, editar, cancelar, coleção). */
    public List<Link> multa(Long id) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(MultaController.class).slash(id).withSelfRel());
        links.add(linkTo(MultaController.class).slash(id).slash("editar").withRel("editar"));
        links.add(linkTo(MultaController.class).slash(id).slash("deletar").withRel("cancelar"));
        links.add(linkTo(MultaController.class).withRel("colecao"));
        return links;
    }

    /** Fila de revisão (self). */
    public List<Link> filaRevisao() {
        return List.of(linkTo(RevisaoController.class).withSelfRel());
    }

    /** Caso de revisão (self, votar, coleção). */
    public List<Link> casoRevisao(Long id) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(RevisaoController.class).slash(id).withSelfRel());
        links.add(linkTo(RevisaoController.class).slash(id).slash("votar").withRel("votar"));
        links.add(linkTo(RevisaoController.class).withRel("colecao"));
        return links;
    }

    /** Dashboard de moderação (self). */
    public List<Link> moderacao() {
        return List.of(linkTo(AdminController.class).slash("moderacao").withSelfRel());
    }

    /** Caso de moderação (self, flag, coleção). */
    public List<Link> casoModeracao(Long id) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(AdminController.class).slash("moderacao").slash(id).withSelfRel());
        links.add(linkTo(AdminController.class).slash("moderacao").slash(id).slash("flag").withRel("flag"));
        links.add(linkTo(AdminController.class).slash("moderacao").withRel("colecao"));
        return links;
    }

    /** Score do usuário (self) e recurso multas. */
    public List<Link> meuScore() {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(ScoreController.class).withSelfRel());
        links.add(linkTo(MultaController.class).withRel("multas"));
        return links;
    }
}