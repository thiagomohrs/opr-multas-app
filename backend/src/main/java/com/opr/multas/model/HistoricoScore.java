package com.opr.multas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "historico_score")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private Integer deltaScore;

    private Integer scoreAntes;

    private Integer scoreDepois;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoScore motivo;

    @ManyToOne
    @JoinColumn(name = "multa_id")
    private Multa multa;

    @Column(nullable = false)
    private LocalDateTime registradoEm;
}
