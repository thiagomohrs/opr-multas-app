package com.opr.multas.service;

import com.opr.multas.config.ModeracaoProperties;
import com.opr.multas.model.HistoricoScore;
import com.opr.multas.model.MotivoScore;
import com.opr.multas.model.Multa;
import com.opr.multas.model.Usuario;
import com.opr.multas.repository.HistoricoScoreRepository;
import com.opr.multas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final UsuarioRepository usuarioRepository;
    private final HistoricoScoreRepository historicoScoreRepository;
    private final ModeracaoProperties props;

    @Transactional
    public void aplicarDelta(Usuario usuario, int delta, MotivoScore motivo, Multa multa) {
        if (usuario == null) {
            return;
        }
        int antes = usuario.getScore() != null ? usuario.getScore() : 0;
        int depois = Math.max(0, antes + delta);

        usuario.setScore(depois);
        usuario.setLastScoreUpdate(LocalDateTime.now());
        usuario.setIsRevisor(depois >= props.getModeracao().getLimiarRevisor());
        usuarioRepository.save(usuario);

        HistoricoScore historico = new HistoricoScore();
        historico.setUsuario(usuario);
        historico.setDeltaScore(delta);
        historico.setScoreAntes(antes);
        historico.setScoreDepois(depois);
        historico.setMotivo(motivo);
        historico.setMulta(multa);
        historico.setRegistradoEm(LocalDateTime.now());
        historicoScoreRepository.save(historico);

        log.info("Score do usuário '{}': {} -> {} ({})", usuario.getLogin(), antes, depois, motivo);
    }
}
