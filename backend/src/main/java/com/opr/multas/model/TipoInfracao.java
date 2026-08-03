package com.opr.multas.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catálogo fixo de tipos de infração exibido como dropdown no cadastro de multa.
 * Cada tipo tem um valor base, preenchido automaticamente no formulário quando o tipo
 * é selecionado (o valor pode ser ajustado manualmente depois).
 */
public record TipoInfracao(String nome, BigDecimal valorBase) {

    public static final List<TipoInfracao> TODOS = List.of(
        new TipoInfracao("Excesso de velocidade (até 20%)", new BigDecimal("130.16")),
        new TipoInfracao("Excesso de velocidade (acima de 20%)", new BigDecimal("195.23")),
        new TipoInfracao("Avanço de sinal vermelho", new BigDecimal("293.47")),
        new TipoInfracao("Conduzir sem cinto de segurança", new BigDecimal("195.23")),
        new TipoInfracao("Uso de celular ao dirigir", new BigDecimal("293.47")),
        new TipoInfracao("Estacionar em local proibido", new BigDecimal("88.38")),
        new TipoInfracao("Dirigir sob efeito de álcool", new BigDecimal("2932.71")),
        new TipoInfracao("Não respeitar faixa de pedestres", new BigDecimal("293.47")),
        new TipoInfracao("Farol desligado / iluminação irregular", new BigDecimal("130.16"))
    );
}