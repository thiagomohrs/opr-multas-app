package com.opr.multas.storage;

import com.opr.multas.model.AnexoMulta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Armazenamento em AWS S3 (imagens/vídeos em bucket).
 *
 * <p>Ativo SOMENTE quando {@code app.storage.type=s3} E o build inclui o profile Maven
 * {@code s3} (que traz o SDK). Como o default é {@code database}, este bean não é criado
 * em produção atual — sem impacto no cold-start.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
@ConditionalOnClass(S3Client.class)
public class S3AnexoStorage implements AnexoStorage {

    private static final String PREFIXO = "multas/anexos/";

    private final S3Client s3Client;
    private final S3Properties properties;

    @Override
    public void armazenar(AnexoMulta anexo) {
        if (anexo.getDados() == null || anexo.getDados().length == 0) {
            return;
        }
        String key = PREFIXO + UUID.randomUUID() + "-" + nomeSeguro(anexo.getNomeOriginal());
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(anexo.getContentType())
                    .contentLength((long) anexo.getDados().length)
                    .build(),
                RequestBody.fromBytes(anexo.getDados()));
            anexo.setS3Key(key);
            anexo.setDados(new byte[0]); // não guarda o blob no banco
            log.info("Anexo enviado ao S3: {}", key);
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar anexo ao S3 (key={})", key, ex);
            throw new IllegalStateException("Falha ao armazenar o anexo no S3.", ex);
        }
    }

    @Override
    public byte[] obter(AnexoMulta anexo) {
        String key = anexo.getS3Key();
        if (key == null || key.isBlank()) {
            return anexo.getDados(); // registros antigos/demo ainda com blob no banco
        }
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build())
                .asByteArray();
        } catch (RuntimeException ex) {
            log.error("Falha ao ler anexo do S3 (key={})", key, ex);
            throw new IllegalStateException("Falha ao ler o anexo do S3.", ex);
        }
    }

    @Override
    public void remover(AnexoMulta anexo) {
        String key = anexo.getS3Key();
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            log.info("Anexo removido do S3: {}", key);
        } catch (RuntimeException ex) {
            log.warn("Falha ao remover anexo do S3 (key={}): {}", key, ex.getMessage());
        }
    }

    private String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "anexo";
        }
        return nome.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}