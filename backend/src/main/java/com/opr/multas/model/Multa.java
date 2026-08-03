package com.opr.multas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "multas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Multa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Placa é obrigatória")
    @Column(nullable = false)
    private String placa;

    @NotBlank(message = "Tipo é obrigatório")
    @Column(nullable = false)
    private String tipo;

    @NotBlank(message = "Descrição é obrigatória")
    @Column(nullable = false)
    private String descricao;

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser positivo")
    @Column(nullable = false)
    private BigDecimal valor;

    @NotNull(message = "Data da infração é obrigatória")
    @Column(nullable = false)
    private LocalDateTime dataInfracao;

    private LocalDateTime dataVencimento;

    @Enumerated(EnumType.STRING)
    private StatusMulta status = StatusMulta.PENDENTE;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusModeracaoMulta statusModeracao = StatusModeracaoMulta.AGUARDANDO_REVISAO;

    @Column(nullable = false)
    private Integer votosNecessarios = 3;

    @Column(name = "peso_votosafavor", nullable = false)
    private Double pesoVotosAFavor = 0.0;

    @Column(name = "peso_votos_contra", nullable = false)
    private Double pesoVotosContra = 0.0;

    private LocalDateTime prazoRevisao;

    @Column(nullable = false)
    private Boolean maliciosa = false;

    @Formula("(select count(*) from votos_revisao vr where vr.multa_id = id)")
    private long votosRegistrados;

    @OneToMany(mappedBy = "multa", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<AnexoMulta> anexos = new ArrayList<>();

    @Formula("(select count(*) from anexos_multa am where am.multa_id = id)")
    private long qtdAnexos;

    public enum StatusMulta {
        PENDENTE, PAGA, CANCELADA
    }
}
