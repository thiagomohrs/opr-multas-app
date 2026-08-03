package com.opr.multas.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Expõe o cliente S3 apenas quando a integração está explicitamente ligada
 * ({@code app.storage.type=s3}) e o SDK está no classpath (build com profile {@code s3}).
 * Desativado por padrão: nada disso é criado em produção atual.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
@ConditionalOnClass(S3Client.class)
@EnableConfigurationProperties(S3Properties.class)
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey())))
            .build();
    }
}