package com.opr.multas.config;

import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import com.opr.multas.repository.MultaRepository;
import com.opr.multas.service.ModeracaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirarCasosJob {

    private final MultaRepository multaRepository;
    private final ModeracaoService moderacaoService;

    @Scheduled(fixedRate = 60000)
    public void expirarCasosVencidos() {
        List<Multa> abertos = multaRepository.findByStatusModeracaoIn(
            List.of(StatusModeracaoMulta.AGUARDANDO_REVISAO, StatusModeracaoMulta.EM_VOTACAO));

        for (Multa multa : abertos) {
            if (multa.getPrazoRevisao() != null && multa.getPrazoRevisao().isBefore(LocalDateTime.now())) {
                moderacaoService.expirarCaso(multa);
            }
        }
    }
}
