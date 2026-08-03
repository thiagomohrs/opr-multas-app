package com.opr.multas.storage;

import com.opr.multas.model.AnexoMulta;

/**
 * Estratégia de armazenamento dos anexos (imagens/vídeos) de uma multa.
 *
 * <p>Duas implementações, selecionadas pela propriedade {@code app.storage.type}:
 * <ul>
 *   <li>{@code database} (padrão): os bytes ficam na coluna {@code dados} do banco.</li>
 *   <li>{@code s3}: o arquivo vai para um bucket AWS S3 e a coluna {@code s3_key}
 *       guarda apenas a chave do objeto (o blob não fica no banco).</li>
 * </ul>
 *
 * <p>Não há bean ativo em modo S3 sem build com o profile Maven {@code s3} (ver pom).</p>
 */
public interface AnexoStorage {

    /**
     * Grava o conteúdo em {@code anexo} no meio de armazenamento ativo.
     * Em DB não faz nada (o byte[] já está no anexo); em S3 envia ao bucket e
     * preenche {@code anexo.s3Key} (limpando {@code dados}).
     */
    void armazenar(AnexoMulta anexo);

    /** Retorna os bytes do objeto. Fallback para {@code anexo.dados} quando não há chave S3. */
    byte[] obter(AnexoMulta anexo);

    /** Remove o objeto do armazenamento (no-op para DB). */
    void remover(AnexoMulta anexo);
}