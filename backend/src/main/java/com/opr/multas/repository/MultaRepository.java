package com.opr.multas.repository;

import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {
    List<Multa> findByPlaca(String placa);
    List<Multa> findByPlacaContainingIgnoreCase(String placa);
    List<Multa> findAllByOrderByIdDesc();
    List<Multa> findByStatusModeracaoIn(List<StatusModeracaoMulta> statuses);
    List<Multa> findByStatusModeracaoInOrderByPrazoRevisaoAsc(List<StatusModeracaoMulta> statuses);
    List<Multa> findByStatusModeracaoInOrderByIdDesc(List<StatusModeracaoMulta> statuses);
}
