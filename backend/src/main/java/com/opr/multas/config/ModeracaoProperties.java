package com.opr.multas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "opr")
@Data
public class ModeracaoProperties {

    private final Moderacao moderacao = new Moderacao();
    private final Score score = new Score();

    @Data
    public static class Moderacao {
        private Integer limiarRevisor = 100;
        private Integer votosNecessarios = 3;
        private Integer prazoRevisaoHoras = 72;
    }

    @Data
    public static class Score {
        private Integer casoAprovadoSolicitante = 10;
        private Integer casoRejeitadoSolicitante = -5;
        private Integer votoCorretoRevisor = 5;
        private Integer votoIncorretoRevisor = -3;
        private Integer votoMaliciosoRevisor = -15;
    }
}
