package com.opr.multas.storage;

import com.opr.multas.model.AnexoMulta;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Armazenamento padrão: os bytes ficam na coluna {@code dados} do banco.
 * Ativo quando {@code app.storage.type=database} (ou a propriedade ausente).
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "database", matchIfMissing = true)
public class DatabaseAnexoStorage implements AnexoStorage {

    @Override
    public void armazenar(AnexoMulta anexo) {
        // AnexoMulta.dados já foi preenchido pelo caller com o byte[] do upload.
    }

    @Override
    public byte[] obter(AnexoMulta anexo) {
        return anexo.getDados();
    }

    @Override
    public void remover(AnexoMulta anexo) {
        // Nada a fazer: o blob é removido junto com a linha do banco.
    }
}