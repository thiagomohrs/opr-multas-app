package com.opr.multas.config;

import com.opr.multas.model.StatusModeracaoMulta;
import com.opr.multas.repository.MultaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirarCasosJob {

    private final ObjectProvider<MultaRepository> multaRepositoryProvider;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirarCasosVencidos() {
        int expirados = multaRepositoryProvider.getObject().expirarAbertosVencidos(
            StatusModeracaoMulta.EXPIRADA,
            List.of(StatusModeracaoMulta.AGUARDANDO_REVISAO, StatusModeracaoMulta.EM_VOTACAO),
            LocalDateTime.now());
        if (expirados > 0) {
            log.info("{} caso(s) expirado(s) sem quórum.", expirados);
        }
    }
}
