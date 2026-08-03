package com.opr.multas.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * Configuração do RabbitMQ para a fila de revisão.
 *
 * <p>Só cria beans quando {@code app.rabbitmq.url} (RABBITMQ_URL) estiver definida.
 * Com a URL vazia (dev/local) não há beans Rabbit e o {@code FilaRevisaoService} usa a
 * seleção por banco. Em produção, a URL é parseada (ex.: amqps://user:pass@host/vhost),
 * com TLS quando o esquema é {@code amqps}. A declaração da fila é tolerante a falhas
 * (Rabbit fora não quebra o boot/cold-start).</p>
 */
@Configuration
public class RabbitConfig {

    public static final String QUEUE_REVISAO = "fila_revisao";

    @Value("${app.rabbitmq.url:}")
    private String rabbitUrl;

    private String url() {
        return rabbitUrl;
    }

    public boolean ativa() {
        return StringUtils.hasText(url());
    }

    @Bean
    @ConditionalOnProperty(name = "app.rabbitmq.url")
    public CachingConnectionFactory rabbitConnectionFactory() {
        String url = url();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("app.rabbitmq.url vazia; RabbitMQ não configurado.");
        }
        try {
            URI uri = URI.create(url);
            com.rabbitmq.client.ConnectionFactory cf = new com.rabbitmq.client.ConnectionFactory();
            cf.setHost(uri.getHost());
            cf.setPort(uri.getPort() > 0 ? uri.getPort() : 5671);
            cf.setUsername(userinfo(uri));
            cf.setPassword(password(uri));
            String vhost = uri.getPath();
            cf.setVirtualHost(StringUtils.hasText(vhost) ? vhost : "/");
            if ("amqps".equalsIgnoreCase(uri.getScheme())) {
                cf.useSslProtocol();
            }
            cf.setConnectionTimeout(3000);
            cf.setRequestedHeartbeat(15);

            CachingConnectionFactory ccf = new CachingConnectionFactory(cf);
            ccf.setConnectionTimeout(3000);
            return ccf;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao configurar RabbitMQ a partir de app.rabbitmq.url.", ex);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.rabbitmq.url")
    public Queue filaRevisaoQueue() {
        return new Queue(QUEUE_REVISAO, true);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rabbitmq.url")
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory rabbitConnectionFactory) {
        return new RabbitTemplate(rabbitConnectionFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rabbitmq.url")
    public AmqpAdmin rabbitAdmin(CachingConnectionFactory rabbitConnectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(rabbitConnectionFactory);
        try {
            admin.declareQueue(new Queue(QUEUE_REVISAO, true));
        } catch (RuntimeException ignored) {
            // Rabbit indisponível: declararás a fil será re-tentada no primeiro publish.
        }
        return admin;
    }

    private String userinfo(URI uri) {
        String ui = uri.getUserInfo();
        return ui == null ? "" : ui.split(":", 2)[0];
    }

    private String password(URI uri) {
        String ui = uri.getUserInfo();
        String[] parts = ui == null ? new String[0] : ui.split(":", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}