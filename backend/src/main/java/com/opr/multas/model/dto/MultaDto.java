package com.opr.multas.model.dto;

import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultaDto {

    private Long id;
    private String placa;
    private String tipo;
    private String descricao;
    private BigDecimal valor;
    private LocalDateTime dataInfracao;
    private LocalDateTime dataVencimento;
    private Multa.StatusMulta status;
    private UsuarioDto usuario;
    private StatusModeracaoMulta statusModeracao;
    private Integer votosNecessarios;
    private Double pesoVotosAFavor;
    private Double pesoVotosContra;
    private LocalDateTime prazoRevisao;
    private Boolean maliciosa;
    private long votosRegistrados;
    private long qtdAnexos;

    /** Constrói um único DTO lendo os contadores do entity (uso pontual/detalhe). */
    public static MultaDto from(Multa m) {
        return from(m, null, null);
    }

    /**
     * Constrói um DTO a partir do entity, usando os mapas de contagem fornecidos
     * (chave = id da multa). Quando os mapas são null, cai de volta nos métodos
     * {@code @Formula} do entity (evita o N+1 em listagens, que passam os mapas).
     */
    public static MultaDto from(Multa m, Map<Long, Long> votosPorMulta, Map<Long, Long> anexosPorMulta) {
        if (m == null) {
            return null;
        }
        long votos = votosPorMulta != null ? votosPorMulta.getOrDefault(m.getId(), 0L) : m.getVotosRegistrados();
        long anexos = anexosPorMulta != null ? anexosPorMulta.getOrDefault(m.getId(), 0L) : m.getQtdAnexos();
        return new MultaDto(
                m.getId(),
                m.getPlaca(),
                m.getTipo(),
                m.getDescricao(),
                m.getValor(),
                m.getDataInfracao(),
                m.getDataVencimento(),
                m.getStatus(),
                UsuarioDto.from(m.getUsuario()),
                m.getStatusModeracao(),
                m.getVotosNecessarios(),
                m.getPesoVotosAFavor(),
                m.getPesoVotosContra(),
                m.getPrazoRevisao(),
                m.getMaliciosa(),
                votos,
                anexos);
    }

    public static List<MultaDto> fromList(List<Multa> multas) {
        return fromList(multas, null, null);
    }

    /** Constrói a lista a partir dos mapas de contagem em lote (sem N+1). */
    public static List<MultaDto> fromList(List<Multa> multas, Map<Long, Long> votosPorMulta, Map<Long, Long> anexosPorMulta) {
        if (multas == null) {
            return new ArrayList<>();
        }
        List<MultaDto> result = new ArrayList<>(multas.size());
        for (Multa m : multas) {
            result.add(from(m, votosPorMulta, anexosPorMulta));
        }
        return result;
    }

    /**
     * Transforma linhas de {@code select x.multas..., count(x)} de uma query de
     * agregação em um mapa {@code id -> total}.
     */
    public static Map<Long, Long> countMap(List<Object[]> linhas) {
        Map<Long, Long> mapa = new HashMap<>();
        if (linhas != null) {
            for (Object[] linha : linhas) {
                mapa.put(((Number) linha[0]).longValue(), ((Number) linha[1]).longValue());
            }
        }
        return mapa;
    }
}