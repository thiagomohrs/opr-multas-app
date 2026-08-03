package com.opr.multas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "anexos_multa")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnexoMulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "multa_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Multa multa;

    @Column(nullable = false)
    private String nomeOriginal;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long tamanhoBytes;

    /** bytes do arquivo quando armazenado no banco (storage=database, default). */
    @Lob
    @Column(nullable = false)
    private byte[] dados;

    /** chave do objeto no bucket (storage=s3). Null quando s3 não está ativo. */
    @Column(name = "s3_key")
    private String s3Key;

    @Column(nullable = false)
    private LocalDateTime enviadoEm;
}
