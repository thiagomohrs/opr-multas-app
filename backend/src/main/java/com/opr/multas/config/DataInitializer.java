package com.opr.multas.config;

import com.opr.multas.model.AnexoMulta;
import com.opr.multas.model.DecisaoVoto;
import com.opr.multas.model.Multa;
import com.opr.multas.model.ProvedorAuth;
import com.opr.multas.model.StatusModeracaoMulta;
import com.opr.multas.model.Usuario;
import com.opr.multas.repository.MultaRepository;
import com.opr.multas.repository.UsuarioRepository;
import com.opr.multas.service.FilaRevisaoService;
import com.opr.multas.service.ModeracaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final ObjectProvider<UsuarioRepository> usuarioRepositoryProvider;
    private final ObjectProvider<MultaRepository> multaRepositoryProvider;
    private final PasswordEncoder passwordEncoder;
    private final ModeracaoProperties props;
    private final ObjectProvider<ModeracaoService> moderacaoServiceProvider;
    private final ObjectProvider<FilaRevisaoService> filaRevisaoServiceProvider;

    private UsuarioRepository usuarioRepository;
    private MultaRepository multaRepository;
    private ModeracaoService moderacaoService;
    private FilaRevisaoService filaRevisaoService;

    @Value("${opr.seed-demo-data:true}")
    private boolean seedDemoData;

    // Senhas dos usuários de demonstração vêm de env (NUNCA hardcode em produção).
    // Ex.: OPR_SEED_ADMIN_SENHA / OPR_SEED_USER_SENHA / OPR_SEED_REVISOR_SENHA
    @Value("${opr.seed.admin-senha:admin123}")
    private String adminSenha;

    @Value("${opr.seed.user-senha:user123}")
    private String userSenha;

    @Value("${opr.seed.revisor-senha:revisor123}")
    private String revisorSenha;

    /**
     * Roda APÓS o boot (ApplicationReadyEvent), em thread própria e tolerante a
     * falhas: o cold-start da Vercel não pode esperar o seed (BCrypt, inserts,
     * geração de imagem). Se o banco estiver indisponível ou o schema ausente,
     * o problema é logado e o container continua de pé.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread seed = new Thread(() -> {
            try {
                run();
            } catch (Exception ex) {
                log.error("Seed de demonstração falhou (boot não bloqueado): {}", ex.getMessage(), ex);
            }
        }, "opr-seed");
        seed.setDaemon(true);
        seed.start();
    }

    public void run() {
        if (!seedDemoData) {
            log.info("Seed de dados de demonstração desabilitado (opr.seed-demo-data=false).");
            return;
        }
        log.info("Inicializando dados de demonstração...");
        this.usuarioRepository = usuarioRepositoryProvider.getObject();
        this.multaRepository = multaRepositoryProvider.getObject();
        this.moderacaoService = moderacaoServiceProvider.getObject();
        this.filaRevisaoService = filaRevisaoServiceProvider.getObject();
        Usuario admin = criarUsuarioSeNaoExiste("admin", adminSenha, "Administrador", "admin@opr.com", "ADMIN", 150, true);
        criarUsuarioSeNaoExiste("user", userSenha, "Usuário Teste", "user@opr.com", "USER", 0, false);
        Usuario revisor1 = criarUsuarioSeNaoExiste("revisor", revisorSenha, "Revisor Teste", "revisor@opr.com", "USER", 130, true);
        Usuario revisor2 = criarUsuarioSeNaoExiste("revisor2", revisorSenha, "Revisor Ana", "revisor2@opr.com", "USER", 110, true);
        Usuario revisor3 = criarUsuarioSeNaoExiste("revisor3", revisorSenha, "Revisor Carlos", "revisor3@opr.com", "USER", 105, true);

        List<Long> casosAbertos = criarCasosDemo(admin, List.of(revisor1, revisor2, revisor3));
        semearFila(casosAbertos);
        log.info("Inicialização de dados concluída.");
    }

    /**
     * Publica os casos demo ainda abertos na fila de revisão (RabbitMQ), garantindo
     * que existam mensagens para os revisores consumirem mesmo quando o seed da fila
     * do boot roda antes da criação dos casos demo. Sem Rabbit (dev), é no-op.
     */
    private void semearFila(List<Long> casosAbertos) {
        if (casosAbertos == null || casosAbertos.isEmpty() || !filaRevisaoService.filaAtiva()) {
            return;
        }
        try {
            filaRevisaoService.publicar(casosAbertos);
            log.info("{} caso(s) demo publicados na fila de revisão.", casosAbertos.size());
        } catch (RuntimeException ex) {
            log.warn("Não foi possível publicar casos demo na fila de revisão: {}", ex.getMessage());
        }
    }

    private Usuario criarUsuarioSeNaoExiste(String login, String senha, String nome, String email,
                                            String role, int score, boolean isRevisor) {
        return usuarioRepository.findByLogin(login).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setLogin(login);
            usuario.setSenha(passwordEncoder.encode(senha));
            usuario.setNome(nome);
            usuario.setEmail(email);
            usuario.setRole(role);
            usuario.setProvider(ProvedorAuth.LOCAL);
            usuario.setScore(score);
            usuario.setIsRevisor(isRevisor);
            usuario.setLastScoreUpdate(LocalDateTime.now());
            try {
                usuarioRepository.save(usuario);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // Instancia concorrente (serverless) pode ter criado o mesmo usuario.
                // Re-tenta a leitura em vez de falhar o startup.
                log.warn("Usuario '{}' ja existe (criado por outra instancia): {}", login, ex.getMessage());
                return usuarioRepository.findByLogin(login).orElse(usuario);
            }
            log.info("Usuário '{}' criado com role '{}' e score {}.", login, role, score);
            return usuario;
        });
    }

    private List<Long> criarCasosDemo(Usuario solicitante, List<Usuario> revisores) {
        List<Long> casosAbertos = new ArrayList<>();

        Multa comAnexo = criarCaso("DEMO-1001", "Excesso de velocidade", solicitante);
        if (comAnexo != null) {
            anexarImagemDemo(comAnexo);
            casosAbertos.add(comAnexo.getId());
        }
        Multa semaforo = criarCaso("DEMO-2002", "Avanço de sinal vermelho", solicitante);
        if (semaforo != null) {
            casosAbertos.add(semaforo.getId());
        }
        Multa estacionamento = criarCaso("DEMO-3003", "Estacionamento proibido", solicitante);
        if (estacionamento != null) {
            casosAbertos.add(estacionamento.getId());
        }

        Multa emVotacao = criarCaso("DEMO-4004", "Uso de celular ao volante", solicitante);
        if (emVotacao != null) {
            registrarVoto(emVotacao.getId(), revisores.get(0), DecisaoVoto.APROVAR);
            casosAbertos.add(emVotacao.getId());
        }

        Multa aprovada = criarCaso("DEMO-5005", "Omissão de documentos", solicitante);
        if (aprovada != null) {
            registrarVoto(aprovada.getId(), revisores.get(0), DecisaoVoto.APROVAR);
            registrarVoto(aprovada.getId(), revisores.get(1), DecisaoVoto.APROVAR);
            registrarVoto(aprovada.getId(), revisores.get(2), DecisaoVoto.APROVAR);
        }

        Multa rejeitada = criarCaso("DEMO-6006", "Notificação não entregue", solicitante);
        if (rejeitada != null) {
            registrarVoto(rejeitada.getId(), revisores.get(0), DecisaoVoto.REJEITAR);
            registrarVoto(rejeitada.getId(), revisores.get(1), DecisaoVoto.REJEITAR);
            registrarVoto(rejeitada.getId(), revisores.get(2), DecisaoVoto.REJEITAR);
        }

        Multa expirada = criarCaso("DEMO-7007", "Multa duplicada", solicitante);
        if (expirada != null) {
            expirada.setStatusModeracao(StatusModeracaoMulta.EXPIRADA);
            expirada.setPrazoRevisao(LocalDateTime.now().minusHours(1));
            multaRepository.save(expirada);
        }

        return casosAbertos;
    }

    private Multa criarCaso(String placa, String tipo, Usuario solicitante) {
        if (!multaRepository.findByPlaca(placa).isEmpty()) {
            return null;
        }
        Multa multa = new Multa();
        multa.setPlaca(placa);
        multa.setTipo(tipo);
        multa.setDescricao("Caso de demonstração para a fila de revisão.");
        multa.setValor(new BigDecimal("195.23"));
        multa.setDataInfracao(LocalDateTime.now().minusDays(5));
        multa.setDataVencimento(LocalDateTime.now().plusDays(25));
        multa.setUsuario(solicitante);
        multa.setStatusModeracao(StatusModeracaoMulta.AGUARDANDO_REVISAO);
        multa.setVotosNecessarios(props.getModeracao().getVotosNecessarios());
        multa.setPesoVotosAFavor(0.0);
        multa.setPesoVotosContra(0.0);
        multa.setPrazoRevisao(LocalDateTime.now().plusHours(props.getModeracao().getPrazoRevisaoHoras()));
        return multaRepository.save(multa);
    }

    private void registrarVoto(Long multaId, Usuario revisor, DecisaoVoto decisao) {
        try {
            moderacaoService.registrarVoto(multaId, revisor, decisao);
        } catch (RuntimeException ex) {
            log.warn("Não foi possível registrar voto demo no caso {}: {}", multaId, ex.getMessage());
        }
    }

    private void anexarImagemDemo(Multa multa) {
        byte[] dados = gerarPngDemo();
        AnexoMulta anexo = new AnexoMulta();
        anexo.setMulta(multa);
        anexo.setNomeOriginal("comprovante-foto.png");
        anexo.setContentType("image/png");
        anexo.setTamanhoBytes((long) dados.length);
        anexo.setDados(dados);
        anexo.setEnviadoEm(LocalDateTime.now());
        multa.getAnexos().add(anexo);
        multaRepository.save(multa);
        log.info("Anexo demo adicionado ao caso '{}'.", multa.getPlaca());
    }

    private byte[] gerarPngDemo() {
        BufferedImage imagem = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        imagem.setRGB(0, 0, 0x1A56DB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagem, "png", out);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar imagem de demonstração.", ex);
        }
        return out.toByteArray();
    }
}
