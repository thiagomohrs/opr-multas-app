package com.opr.multas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String login;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true)
    private String email;

    private String role = "USER";

    private Integer score = 0;

    private Boolean isRevisor = false;

    private LocalDateTime lastScoreUpdate;

    @Enumerated(EnumType.STRING)
    private ProvedorAuth provider = ProvedorAuth.LOCAL;

    @Column(unique = true)
    private String googleSub;

    private String avatarUrl;
}
