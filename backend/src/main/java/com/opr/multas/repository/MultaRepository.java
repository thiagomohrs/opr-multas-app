package com.opr.multas.repository;

import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {
    List<Multa> findByPlaca(String placa);
    List<Multa> findByPlacaContainingIgnoreCase(String placa);
    List<Multa> findAllByOrderByIdDesc();
    List<Multa> findByStatusModeracaoIn(List<StatusModeracaoMulta> statuses);
    List<Multa> findByStatusModeracaoInOrderByPrazoRevisaoAsc(List<StatusModeracaoMulta> statuses);
    List<Multa> findByStatusModeracaoInOrderByIdDesc(List<StatusModeracaoMulta> statuses);
    List<Multa> findByUsuarioIdOrderByIdDesc(Long usuarioId);
    List<Multa> findByUsuarioIdAndPlacaContainingIgnoreCaseOrderByIdDesc(Long usuarioId, String placa);

    /** Contagem de casos por status de moderação (dashboard). */
    @Query("select m.statusModeracao, count(m) from Multa m group by m.statusModeracao")
    List<Object[]> countAgrupadoPorStatusModeracao();

    /**
     * Expira num único UPDATE todos os casos ainda abertos cujo prazo de revisão já venceu.
     * Substitui o carregamento+save caso a caso do ExpirarCasosJob (menos consultas e
     * sem correr o race de sobrescrever um caso recém-resolvido com entidade obsoleta).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Multa m set m.statusModeracao = :expirado " +
           "where m.statusModeracao in :abertos and m.prazoRevisao < :agora")
    int expirarAbertosVencidos(@Param("expirado") StatusModeracaoMulta expirado,
                               @Param("abertos") List<StatusModeracaoMulta> abertos,
                               @Param("agora") LocalDateTime agora);

    /** Contagem de votos por multa, em uma única query (evita o N+1 do @Formula). */
    @Query("select v.multa.id, count(v) from VotoRevisao v where v.multa.id in :ids group by v.multa.id")
    List<Object[]> countVotosPorMultaIdIn(@Param("ids") Collection<Long> ids);

    /** Contagem de anexos por multa, em uma única query (evita o N+1 do @Formula). */
    @Query("select a.multa.id, count(a) from AnexoMulta a where a.multa.id in :ids group by a.multa.id")
    List<Object[]> countAnexosPorMultaIdIn(@Param("ids") Collection<Long> ids);
}
