package com.opr.multas.repository;

import com.opr.multas.model.AnexoMulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnexoMultaRepository extends JpaRepository<AnexoMulta, Long> {
}
