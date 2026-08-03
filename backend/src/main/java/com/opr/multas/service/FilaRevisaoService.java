package com.opr.multas.service;

import com.opr.multas.config.RabbitConfig;
import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import com.opr.multas.model.Usuario;
import com.opr.multas.repository.MultaRepository;
import com.opr.multas.repository.VotoRevisaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fila de revisão baseada no Spring AMQP (RabbitMQ).
 *
 * <p>Os revisores NÃO escolhem qual caso analisar: o sistema entrega o próximo caso
 * da fila {@code fila_revisao}. As regras de deduplicação (o mesmo revisor não analisa
 * a mesma multa mais de uma vez) e de elegibilidade são aplicadas na entrega e também
 * na votação ({@code ModeracaoService.validarElegibilidadeRevisor}).</p>
 *
 * <p>Quando o Rabbit está desabilitado (dev, sem {@code app.rabbitmq.url}), cai para a
 * seleção por banco apenas para testes locais.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilaRevisaoService {

    private static final long RECEIVE_TIMEOUT_MS = 200;
    private static final int MAX_TENTATIVAS_FILA = 100;

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final ObjectProvider<AmqpAdmin> amqpAdminProvider;
    private final MultaRepository multaRepository;
    private final VotoRevisaoRepository votoRepository;

    public boolean filaAtiva() {
        return rabbitTemplateProvider.getIfAvailable() != null;
    }

    /**
     * Seed em boot: publica os casos ainda abertos na fila, para reencher a fila após
     * restart. Tolerante a Rabbit indisponível; roda após o ApplicationReadyEvent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedFilaNoBoot() {
        Thread t = new Thread(() -> {
            try {
                List<Multa> abertas = multaRepository.findByStatusModeracaoIn(List.of(
                    StatusModeracaoMulta.AGUARDANDO_REVISAO, StatusModeracaoMulta.EM_VOTACAO));
                publicar(abertas.stream().map(Multa::getId).toList());
                log.info("Fila de revisão semeada com {} caso(s) aberto(s).", abertas.size());
            } catch (RuntimeException ex) {
                log.warn("Seed da fila de revisão falhou (não bloqueia boot): {}", ex.getMessage());
            }
        }, "opr-fila-seed");
        t.setDaemon(true);
        t.start();
    }

    /** Publica um id de caso na fila (mesmo id mais de uma vez é inofensivo: dedup na entrega). */
    public void publicar(Long multaId) {
        RabbitTemplate t = rabbitTemplateProvider.getIfAvailable();
        if (t == null) {
            return;
        }
        garantirQueue();
        try {
            t.convertAndSend(RabbitConfig.QUEUE_REVISAO, String.valueOf(multaId));
        } catch (RuntimeException ex) {
            log.warn("Erro ao publicar caso {} na fila: {}", multaId, ex.getMessage());
        }
    }

    /** Publica vários ids de uma vez. */
    public void publicar(List<Long> multaIds) {
        RabbitTemplate t = rabbitTemplateProvider.getIfAvailable();
        if (t == null) {
            return;
        }
        garantirQueue();
        for (Long id : multaIds) {
            try {
                t.convertAndSend(RabbitConfig.QUEUE_REVISAO, String.valueOf(id));
            } catch (RuntimeException ex) {
                log.warn("Erro ao publicar caso {} na fila: {}", id, ex.getMessage());
            }
        }
    }

    /**
     * Entrega o próximo caso para o revisor analisar. Prioriza a fila Rabbit; se
     * esvaziar (ou Rabbit desativado), cai para a seleção por banco do próximo caso
     * elegível — em qualquer caso o revisor não escolhe o caso.
     */
    public Long proximoCaso(Usuario revisor) {
        if (filaAtiva()) {
            Long daFila = consumirDaFila(revisor);
            if (daFila != null) {
                return daFila;
            }
        }
        return proximoPorBanco(revisor);
    }

    /** Após um voto, se o caso continua aberto, devolve-o à fila para outros revisores. */
    public void devolverSeAberto(Multa multa) {
        if (multa.getStatusModeracao() == StatusModeracaoMulta.AGUARDANDO_REVISAO
            || multa.getStatusModeracao() == StatusModeracaoMulta.EM_VOTACAO) {
            publicar(multa.getId());
        }
    }

    /** {@link #devolverSeAberto(Multa)} por id (carrega a entidade atual do banco). */
    public void devolverSeAberto(Long multaId) {
        multaRepository.findById(multaId).ifPresent(this::devolverSeAberto);
    }

    private Long consumirDaFila(Usuario revisor) {
        RabbitTemplate t = rabbitTemplateProvider.getIfAvailable();
        if (t == null) {
            return null;
        }
        garantirQueue();
        for (int i = 0; i < MAX_TENTATIVAS_FILA; i++) {
            Message msg;
            try {
                msg = t.receive(RabbitConfig.QUEUE_REVISAO, RECEIVE_TIMEOUT_MS);
            } catch (RuntimeException ex) {
                log.debug("Fila indisponível ao receber: {}", ex.getMessage());
                return null;
            }
            if (msg == null) {
                return null;
            }
            Long id = parseId(msg);
            if (id == null) {
                continue;
            }
            Multa multa = multaRepository.findById(id).orElse(null);
            if (multa == null) {
                continue;
            }
            if (!aberta(multa)) {
                continue; // resolvido/expirado: descarta da fila.
            }
            if (!podeRevisar(revisor, multa)) {
                // Não serve para ESTE revisor, mas o caso ainda precisa de votos: devolve p/ fila.
                t.convertAndSend(RabbitConfig.QUEUE_REVISAO, String.valueOf(id));
                continue;
            }
            return id;
        }
        return null;
    }

    private Long proximoPorBanco(Usuario revisor) {
        List<Multa> abertas = multaRepository.findByStatusModeracaoInOrderByPrazoRevisaoAsc(
            List.of(StatusModeracaoMulta.AGUARDANDO_REVISAO, StatusModeracaoMulta.EM_VOTACAO));
        return abertas.stream()
            .map(Multa::getId)
            .filter(id -> multaRepository.findById(id).map(m -> podeRevisar(revisor, m)).orElse(false))
            .findFirst()
            .orElse(null);
    }

    private boolean podeRevisar(Usuario revisor, Multa multa) {
        if (revisor == null || multa == null) {
            return false;
        }
        if (!aberta(multa)) {
            return false;
        }
        if (multa.getUsuario() != null && multa.getUsuario().getId().equals(revisor.getId())) {
            return false;
        }
        return !votoRepository.existsByRevisorAndMulta(revisor, multa);
    }

    private boolean aberta(Multa multa) {
        if (multa.getStatusModeracao() != StatusModeracaoMulta.AGUARDANDO_REVISAO
            && multa.getStatusModeracao() != StatusModeracaoMulta.EM_VOTACAO) {
            return false;
        }
        return multa.getPrazoRevisao() == null || !multa.getPrazoRevisao().isBefore(LocalDateTime.now());
    }

    private Long parseId(Message msg) {
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8).trim();
            if (!StringUtils.hasText(body)) {
                return null;
            }
            return Long.valueOf(body);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void garantirQueue() {
        AmqpAdmin admin = amqpAdminProvider.getIfAvailable();
        if (admin != null) {
            try {
                admin.declareQueue(new org.springframework.amqp.core.Queue(RabbitConfig.QUEUE_REVISAO, true));
            } catch (RuntimeException ignored) {
                // re-tentada no próximo publish.
            }
        }
    }
}