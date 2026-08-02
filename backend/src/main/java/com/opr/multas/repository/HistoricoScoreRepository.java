package com.opr.multas.repository;

import com.opr.multas.model.HistoricoScore;
import com.opr.multas.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoricoScoreRepository extends JpaRepository<HistoricoScore, Long> {
    List<HistoricoScore> findByUsuarioOrderByRegistradoEmDesc(Usuario usuario);
}
