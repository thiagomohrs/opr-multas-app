package com.opr.multas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "votos_revisao", uniqueConstraints = @UniqueConstraint(columnNames = {"revisor_id", "multa_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotoRevisao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "revisor_id", nullable = false)
    private Usuario revisor;

    @ManyToOne
    @JoinColumn(name = "multa_id", nullable = false)
    private Multa multa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisaoVoto decisao;

    @Column(nullable = false)
    private Integer scoreRevisorNoMomento;

    @Column(nullable = false)
    private Double pesoDoVoto;

    @Column(nullable = false)
    private LocalDateTime votadoEm;

    private Boolean feedbackAplicado = false;

    private Integer deltaScore;
}
