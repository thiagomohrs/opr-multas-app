package com.opr.multas.service;

import com.opr.multas.config.CacheConfig;
import com.opr.multas.config.ModeracaoProperties;
import com.opr.multas.model.DecisaoVoto;
import com.opr.multas.model.MotivoScore;
import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import com.opr.multas.model.Usuario;
import com.opr.multas.model.VotoRevisao;
import com.opr.multas.model.dto.MultaDto;
import com.opr.multas.repository.MultaRepository;
import com.opr.multas.repository.VotoRevisaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeracaoService {

    private final MultaRepository multaRepository;
    private final VotoRevisaoRepository votoRepository;
    private final ScoreService scoreService;
    private final ModeracaoProperties props;

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_MULTAS, CacheConfig.CACHE_FILA_REVISAO, CacheConfig.CACHE_CASOS_RESOLVIDOS, CacheConfig.CACHE_MODERACAO_CASOS}, allEntries = true)
    public void registrarVoto(Long multaId, Usuario revisor, DecisaoVoto decisao) {
        Multa multa = multaRepository.findById(multaId)
            .orElseThrow(() -> new IllegalArgumentException("Caso não encontrado: " + multaId));

        validarCasoAberto(multa);
        validarElegibilidadeRevisor(revisor, multa);

        int scoreRevisor = revisor.getScore() != null ? revisor.getScore() : 0;
        double peso = (double) scoreRevisor / props.getModeracao().getLimiarRevisor();

        VotoRevisao voto = new VotoRevisao();
        voto.setRevisor(revisor);
        voto.setMulta(multa);
        voto.setDecisao(decisao);
        voto.setScoreRevisorNoMomento(scoreRevisor);
        voto.setPesoDoVoto(peso);
        voto.setVotadoEm(LocalDateTime.now());
        votoRepository.save(voto);

        if (decisao == DecisaoVoto.APROVAR) {
            multa.setPesoVotosAFavor(multa.getPesoVotosAFavor() + peso);
        } else {
            multa.setPesoVotosContra(multa.getPesoVotosContra() + peso);
        }

        if (multa.getStatusModeracao() == StatusModeracaoMulta.AGUARDANDO_REVISAO) {
            multa.setStatusModeracao(StatusModeracaoMulta.EM_VOTACAO);
        }
        multaRepository.save(multa);

        log.info("Voto {} de '{}' no caso {} (peso {})", decisao, revisor.getLogin(), multaId, peso);

        long totalVotos = votoRepository.countByMulta(multa);
        if (totalVotos >= multa.getVotosNecessarios()) {
            resolverCaso(multa);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_MULTAS, CacheConfig.CACHE_FILA_REVISAO, CacheConfig.CACHE_CASOS_RESOLVIDOS, CacheConfig.CACHE_MODERACAO_CASOS}, allEntries = true)
    public void resolverCaso(Multa multa) {
        double totalPeso = multa.getPesoVotosAFavor() + multa.getPesoVotosContra();
        double ratioFavor = totalPeso == 0 ? 0 : multa.getPesoVotosAFavor() / totalPeso;

        StatusModeracaoMulta resultado = ratioFavor >= 0.5
            ? StatusModeracaoMulta.APROVADA
            : StatusModeracaoMulta.REJEITADA;

        multa.setStatusModeracao(resultado);
        multaRepository.save(multa);

        log.info("Caso {} resolvido como {} (peso favor: {})", multa.getId(), resultado, ratioFavor);
        aplicarFeedbackScore(multa, resultado);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_MULTAS, CacheConfig.CACHE_FILA_REVISAO, CacheConfig.CACHE_CASOS_RESOLVIDOS, CacheConfig.CACHE_MODERACAO_CASOS}, allEntries = true)
    public void expirarCaso(Multa multa) {
        if (multa.getStatusModeracao() == StatusModeracaoMulta.AGUARDANDO_REVISAO
            || multa.getStatusModeracao() == StatusModeracaoMulta.EM_VOTACAO) {
            multa.setStatusModeracao(StatusModeracaoMulta.EXPIRADA);
            multaRepository.save(multa);
            log.info("Caso {} expirado sem quórum", multa.getId());
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_MULTAS, CacheConfig.CACHE_FILA_REVISAO, CacheConfig.CACHE_CASOS_RESOLVIDOS, CacheConfig.CACHE_MODERACAO_CASOS}, allEntries = true)
    public void flagCasoMalicioso(Long multaId) {
        Multa multa = multaRepository.findById(multaId)
            .orElseThrow(() -> new IllegalArgumentException("Caso não encontrado: " + multaId));

        if (Boolean.TRUE.equals(multa.getMaliciosa())) {
            throw new IllegalStateException("Este caso já foi marcado como malicioso.");
        }

        multa.setMaliciosa(true);
        multa.setStatusModeracao(StatusModeracaoMulta.REJEITADA);
        multaRepository.save(multa);

        for (VotoRevisao voto : votoRepository.findByMultaOrderByVotadoEmAsc(multa)) {
            if (voto.getDecisao() == DecisaoVoto.APROVAR && !Boolean.TRUE.equals(voto.getFeedbackAplicado())) {
                int delta = props.getScore().getVotoMaliciosoRevisor();
                voto.setFeedbackAplicado(true);
                voto.setDeltaScore(delta);
                votoRepository.save(voto);
                scoreService.aplicarDelta(voto.getRevisor(), delta, MotivoScore.VOTO_MALICIOSO, multa);
            }
        }
        log.info("Caso {} marcado como malicioso", multaId);
    }

    private void validarCasoAberto(Multa multa) {
        if (multa.getStatusModeracao() != StatusModeracaoMulta.AGUARDANDO_REVISAO
            && multa.getStatusModeracao() != StatusModeracaoMulta.EM_VOTACAO) {
            throw new IllegalStateException("Este caso não está aberto para votação.");
        }
        if (multa.getPrazoRevisao() != null && multa.getPrazoRevisao().isBefore(LocalDateTime.now())) {
            expirarCaso(multa);
            throw new IllegalStateException("O prazo de revisão deste caso expirou.");
        }
    }

    private void validarElegibilidadeRevisor(Usuario revisor, Multa multa) {
        if (revisor == null) {
            throw new IllegalStateException("Usuário não identificado.");
        }
        if (multa.getUsuario() != null && multa.getUsuario().getId().equals(revisor.getId())) {
            throw new IllegalStateException("O solicitante não pode votar no próprio caso.");
        }
        if (votoRepository.existsByRevisorAndMulta(revisor, multa)) {
            throw new IllegalStateException("Você já votou neste caso.");
        }
        boolean admin = "ADMIN".equalsIgnoreCase(revisor.getRole());
        boolean revisorElegivel = Boolean.TRUE.equals(revisor.getIsRevisor())
            || (revisor.getScore() != null && revisor.getScore() >= props.getModeracao().getLimiarRevisor());
        if (!admin && !revisorElegivel) {
            throw new IllegalStateException("Score insuficiente para revisar casos.");
        }
    }

    private void aplicarFeedbackScore(Multa multa, StatusModeracaoMulta resultado) {
        if (multa.getUsuario() != null) {
            int delta = resultado == StatusModeracaoMulta.APROVADA
                ? props.getScore().getCasoAprovadoSolicitante()
                : props.getScore().getCasoRejeitadoSolicitante();
            MotivoScore motivo = resultado == StatusModeracaoMulta.APROVADA
                ? MotivoScore.CASO_APROVADO
                : MotivoScore.CASO_REJEITADO;
            scoreService.aplicarDelta(multa.getUsuario(), delta, motivo, multa);
        }

        for (VotoRevisao voto : votoRepository.findByMultaAndFeedbackAplicadoFalse(multa)) {
            boolean alinhado = (voto.getDecisao() == DecisaoVoto.APROVAR)
                == (resultado == StatusModeracaoMulta.APROVADA);
            int delta = alinhado
                ? props.getScore().getVotoCorretoRevisor()
                : props.getScore().getVotoIncorretoRevisor();
            MotivoScore motivo = alinhado ? MotivoScore.VOTO_CORRETO : MotivoScore.VOTO_INCORRETO;

            voto.setFeedbackAplicado(true);
            voto.setDeltaScore(delta);
            votoRepository.save(voto);
            scoreService.aplicarDelta(voto.getRevisor(), delta, motivo, multa);
        }
    }

    @Transactional(readOnly = true)
    public Multa buscarCasoPorId(Long id) {
        Multa multa = multaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Caso não encontrado: " + id));
        // Inicializa a coleção de anexos dentro da transação (open-in-view desativado).
        multa.getAnexos().size();
        return multa;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_FILA_REVISAO)
    public List<MultaDto> listarFilaRevisao() {
        return lerLista(multaRepository.findByStatusModeracaoInOrderByPrazoRevisaoAsc(
            List.of(StatusModeracaoMulta.AGUARDANDO_REVISAO, StatusModeracaoMulta.EM_VOTACAO)));
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_CASOS_RESOLVIDOS)
    public List<MultaDto> listarCasosResolvidos() {
        return lerLista(multaRepository.findByStatusModeracaoInOrderByIdDesc(
            List.of(StatusModeracaoMulta.APROVADA, StatusModeracaoMulta.REJEITADA, StatusModeracaoMulta.EXPIRADA)));
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_MODERACAO_CASOS)
    public List<MultaDto> listarTodosCasos() {
        return lerLista(multaRepository.findByStatusModeracaoInOrderByIdDesc(
            List.of(StatusModeracaoMulta.values())));
    }

    /** Monta DTOs com contadores agregados em lote (evita o N+1 do @Formula). */
    private List<MultaDto> lerLista(List<Multa> entidades) {
        if (entidades.isEmpty()) {
            return List.of();
        }
        List<Long> ids = entidades.stream().map(Multa::getId).toList();
        Map<Long, Long> votos = MultaDto.countMap(multaRepository.countVotosPorMultaIdIn(ids));
        Map<Long, Long> anexos = MultaDto.countMap(multaRepository.countAnexosPorMultaIdIn(ids));
        return MultaDto.fromList(entidades, votos, anexos);
    }
}
