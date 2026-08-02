package com.opr.multas.repository;

import com.opr.multas.model.Multa;
import com.opr.multas.model.Usuario;
import com.opr.multas.model.VotoRevisao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VotoRevisaoRepository extends JpaRepository<VotoRevisao, Long> {
    long countByMulta(Multa multa);
    boolean existsByRevisorAndMulta(Usuario revisor, Multa multa);
    Optional<VotoRevisao> findByRevisorAndMulta(Usuario revisor, Multa multa);
    List<VotoRevisao> findByMultaOrderByVotadoEmAsc(Multa multa);
    List<VotoRevisao> findByMultaAndFeedbackAplicadoFalse(Multa multa);
    List<VotoRevisao> findByRevisorOrderByVotadoEmDesc(Usuario revisor);
}
