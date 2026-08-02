package com.opr.multas.service;

import com.opr.multas.config.ModeracaoProperties;
import com.opr.multas.model.AnexoMulta;
import com.opr.multas.model.Multa;
import com.opr.multas.model.StatusModeracaoMulta;
import com.opr.multas.model.Usuario;
import com.opr.multas.repository.MultaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultaService {

    public static final long MAX_ANEXO_BYTES = 10L * 1024 * 1024;

    private static final Set<String> EXTENSOES_IMAGEM = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");
    private static final Set<String> EXTENSOES_VIDEO = Set.of("mp4", "webm", "mov", "m4v", "mkv", "avi");

    private final MultaRepository multaRepository;
    private final ModeracaoProperties props;

    public List<Multa> listarTodas() {
        return multaRepository.findAllByOrderByIdDesc();
    }

    public List<Multa> listarPorPlaca(String placa) {
        return multaRepository.findByPlacaContainingIgnoreCase(placa);
    }

    public Multa buscarEntidadePorId(Long id) {
        return multaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Multa não encontrada: " + id));
    }

    @Transactional
    public Multa criar(Multa multa, Usuario solicitante, MultipartFile[] arquivos) {
        aplicarDefaultsModeracao(multa);
        multa.setUsuario(solicitante);
        multaRepository.save(multa);
        anexarArquivos(multa, arquivos);
        log.info("Criando multa para placa: {}", multa.getPlaca());
        return multaRepository.save(multa);
    }

    @Transactional
    public Multa atualizar(Long id, Multa novosDados, MultipartFile[] arquivos) {
        Multa multa = buscarEntidadePorId(id);
        multa.setPlaca(novosDados.getPlaca());
        multa.setTipo(novosDados.getTipo());
        multa.setDescricao(novosDados.getDescricao());
        multa.setValor(novosDados.getValor());
        multa.setDataInfracao(novosDados.getDataInfracao());
        multa.setDataVencimento(novosDados.getDataVencimento());
        if (novosDados.getStatus() != null) {
            multa.setStatus(novosDados.getStatus());
        }
        anexarArquivos(multa, arquivos);
        log.info("Multa {} atualizada", id);
        return multaRepository.save(multa);
    }

    @Transactional
    public void deletar(Long id) {
        log.info("Deletando multa {}", id);
        multaRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public AnexoMulta buscarAnexo(Long multaId, Long anexoId) {
        Multa multa = buscarEntidadePorId(multaId);
        return multa.getAnexos().stream()
            .filter(anexo -> anexo.getId().equals(anexoId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado: " + anexoId));
    }

    @Transactional
    public void removerAnexo(Long multaId, Long anexoId) {
        Multa multa = buscarEntidadePorId(multaId);
        boolean removido = multa.getAnexos().removeIf(anexo -> anexo.getId().equals(anexoId));
        if (!removido) {
            throw new IllegalArgumentException("Anexo não encontrado: " + anexoId);
        }
        multaRepository.save(multa);
        log.info("Anexo {} removido da multa {}", anexoId, multaId);
    }

    private void anexarArquivos(Multa multa, MultipartFile[] arquivos) {
        log.info("DEBUG anexarArquivos: arquivos={}", arquivos == null ? "null" : arquivos.length);
        if (arquivos == null) {
            return;
        }
        for (MultipartFile arquivo : arquivos) {
            if (arquivo == null || arquivo.isEmpty()) {
                continue;
            }
            validarAnexo(arquivo);
            AnexoMulta anexo = new AnexoMulta();
            anexo.setMulta(multa);
            anexo.setNomeOriginal(arquivo.getOriginalFilename());
            anexo.setContentType(arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream");
            anexo.setTamanhoBytes(arquivo.getSize());
            anexo.setEnviadoEm(LocalDateTime.now());
            try {
                anexo.setDados(arquivo.getBytes());
            } catch (IOException ex) {
                throw new IllegalStateException("Falha ao ler o arquivo enviado.", ex);
            }
            multa.getAnexos().add(anexo);
        }
    }

    private void validarAnexo(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();
        if (arquivo.getSize() > MAX_ANEXO_BYTES) {
            throw new IllegalArgumentException("O arquivo \"" + nome + "\" excede o limite de 10 MB.");
        }
        String contentType = arquivo.getContentType();
        boolean tipoPermitido = contentType != null
            && (contentType.startsWith("image/") || contentType.startsWith("video/"));
        if (!tipoPermitido) {
            String extensao = extensao(nome);
            tipoPermitido = EXTENSOES_IMAGEM.contains(extensao) || EXTENSOES_VIDEO.contains(extensao);
        }
        if (!tipoPermitido) {
            throw new IllegalArgumentException("Somente imagens e vídeos são permitidos: \"" + nome + "\".");
        }
    }

    private String extensao(String nome) {
        if (nome == null || !nome.contains(".")) {
            return "";
        }
        return nome.substring(nome.lastIndexOf('.') + 1).toLowerCase();
    }

    private void aplicarDefaultsModeracao(Multa multa) {
        if (multa.getStatusModeracao() == null) {
            multa.setStatusModeracao(StatusModeracaoMulta.AGUARDANDO_REVISAO);
        }
        if (multa.getVotosNecessarios() == null) {
            multa.setVotosNecessarios(props.getModeracao().getVotosNecessarios());
        }
        if (multa.getPrazoRevisao() == null) {
            multa.setPrazoRevisao(LocalDateTime.now().plusHours(props.getModeracao().getPrazoRevisaoHoras()));
        }
        if (multa.getPesoVotosAFavor() == null) {
            multa.setPesoVotosAFavor(0.0);
        }
        if (multa.getPesoVotosContra() == null) {
            multa.setPesoVotosContra(0.0);
        }
    }
}
