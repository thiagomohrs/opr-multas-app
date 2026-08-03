package com.opr.multas.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da integração AWS S3 ({@code app.storage.s3.*}). Só é usada quando
 * {@code app.storage.type=s3}. Preenchida via variáveis de ambiente (da AWS).
 */
@Data
@ConfigurationProperties(prefix = "app.storage.s3")
public class S3Properties {

    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
}