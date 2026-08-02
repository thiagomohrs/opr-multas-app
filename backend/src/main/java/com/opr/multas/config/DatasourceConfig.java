package com.opr.multas.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class DatasourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfig.class);

    /**
     * Aceita DATABASE_URL em três formatos:
     *  1. postgresql://user:pass@host:5432/db   (formato nativo do Supabase, convertido para JDBC)
     *  2. jdbc:postgresql://host:5432/db?user=..&password=..  (credenciais na query string)
     *  3. jdbc:postgresql://host:5432/db       (usa DATABASE_USER/DATABASE_PASSWORD)
     * Em todos os casos as credenciais presentes na URL têm prioridade sobre properties/env
     * e o sslmode é garantido (prefer, se ausente).
     */
    private static final Pattern URL_NAO_JDBC = Pattern.compile(
        "^postgres(?:ql)?://(?:(.*)@)?([^/?#]+)(?:/([^?]*))?(?:\\?(.*))?$");

    @Value("${spring.datasource.hikari.initialization-fail-timeout:1}")
    private long initializationFailTimeout;

    // Envs da integração Vercel x Supabase. Usadas quando DATABASE_URL está ausente
    // ou malformada (ex.: apenas o host, sem esquema jdbc:postgresql://).
    @Value("${POSTGRES_URL:}")
    private String postgresUrl;
    @Value("${POSTGRES_URL_NON_POOLING:}")
    private String postgresUrlNonPooling;
    @Value("${POSTGRES_PRISMA_URL:}")
    private String postgresPrismaUrl;
    @Value("${POSTGRES_DATABASE:}")
    private String postgresDatabase;

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        String username = properties.getUsername();
        String password = properties.getPassword();

        if (url != null) {
            if (isUrlPostgresValida(url)) {
                // URL válida (jdbc:postgresql://, postgresql:// ou postgres://):
                // normaliza e extrai credenciais conforme o formato.
                UrlInfo info = normalizar(url);
                url = info.jdbcUrl;
                if (StringUtils.hasText(info.username)) {
                    username = info.username;
                }
                if (StringUtils.hasText(info.password)) {
                    password = info.password;
                }
            } else if (!url.startsWith("jdbc:h2")) {
                // DATABASE_URL presente porém inválida (ex.: só o host, sem esquema).
                // Tenta as URLs da integração Supabase (pooler IPv4, credenciais reais);
                // caso contrário, monta uma JDBC completa a partir do host informado.
                String candidata = primeiraUrlSupabaseValida();
                if (candidata != null) {
                    UrlInfo info = normalizar(candidata);
                    url = info.jdbcUrl;
                    if (StringUtils.hasText(info.username)) {
                        username = info.username;
                    }
                    if (StringUtils.hasText(info.password)) {
                        password = info.password;
                    }
                    log.warn("DATABASE_URL inválida '{}'; usando URL da integração Supabase.", properties.getUrl());
                } else {
                    url = montarJdbcDeHost(properties.getUrl());
                    log.warn("DATABASE_URL sem esquema JDBC; montada conexão para host {}.", properties.getUrl());
                }
            }
        }

        log.info("DataSource configurada (JDBC): {} (usuario: {})", url,
            StringUtils.hasText(username) ? username : "(via URL)");
        if (StringUtils.hasText(password) && password.contains("YOUR-PASSWORD")) {
            log.warn("A senha do banco parece ser o placeholder 'YOUR-PASSWORD'. Defina a senha real "
                + "na env DATABASE_URL (Project Settings -> Environment Variables na Vercel).");
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(garantirPreparedStatementsOff(url));
        if (StringUtils.hasText(username)) {
            ds.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            ds.setPassword(password);
        }
        ds.setInitializationFailTimeout(initializationFailTimeout);
        return ds;
    }

    /**
     * Desativa prepared statements server-side (prepareThreshold=0).
     * Necessário quando o pooler do Supabase (porta 6543, transaction-mode) é
     * usado: ele não suporta prepared statements nomeados do driver JDBC e a app
     * falha com "prepared statement \"S_1\" already exists". É inócuo em conexão
     * direta (porta 5432), apenas evita o cache no servidor.
     */
    private String garantirPreparedStatementsOff(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql")) {
            return url;
        }
        String separador = url.indexOf('?') >= 0 ? "&" : "?";
        return url + separador + "prepareThreshold=0";
    }

    private boolean isUrlPostgresValida(String url) {
        return url.startsWith("jdbc:postgresql://")
            || url.startsWith("postgresql://")
            || url.startsWith("postgres://");
    }

    /**
     * Prefere a URL NÃO-pooling (porta 5432, conexão direta ao Supabase): suporta
     * prepared statements nativamente e não depende do PgBouncer/Supavisor.
     * O pooler (porta 6543, transaction-mode) não suporta prepared statements do
     * driver JDBC — se for usado, o DatasourceConfig adiciona prepareThreshold=0.
     */
    private String primeiraUrlSupabaseValida() {
        for (String candidata : new String[] {postgresUrlNonPooling, postgresUrl, postgresPrismaUrl}) {
            if (StringUtils.hasText(candidata) && isUrlPostgresValida(candidata)) {
                return candidata;
            }
        }
        return null;
    }

    private String montarJdbcDeHost(String host) {
        String db = StringUtils.hasText(postgresDatabase) ? postgresDatabase : "postgres";
        return "jdbc:postgresql://" + host + ":5432/" + db + "?sslmode=require";
    }

    private UrlInfo normalizar(String url) {
        if (url.startsWith("jdbc:postgresql://")) {
            return extrairCredenciaisDaQuery(url);
        }
        return converterParaJdbc(url);
    }

    /**
     * Converte postgresql://user:pass@host:5432/db (e eventuais params) para jdbc:postgresql://...
     * aplicando URL-decode nas credenciais (o painel do Supabase entrega a senha percent-encodada).
     */
    private UrlInfo converterParaJdbc(String url) {
        Matcher m = URL_NAO_JDBC.matcher(url);
        if (!m.matches()) {
            throw new IllegalArgumentException("URL de banco inválida: " + url);
        }

        String userinfo = m.group(1);
        String host = m.group(2);
        String db = m.group(3);
        String params = m.group(4);

        String user = null;
        String pass = null;
        if (userinfo != null && !userinfo.isBlank()) {
            int idx = userinfo.indexOf(':');
            if (idx >= 0) {
                user = decodificar(userinfo.substring(0, idx));
                pass = decodificar(userinfo.substring(idx + 1));
            } else {
                user = decodificar(userinfo);
            }
        }

        if (db == null || db.isBlank()) {
            db = "postgres";
        }

        StringBuilder query = new StringBuilder();
        if (params != null && !params.isBlank()) {
            query.append(params);
        }
        if (!query.toString().contains("sslmode")) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append("sslmode=prefer");
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host).append('/').append(db);
        if (query.length() > 0) {
            jdbc.append('?').append(query);
        }

        return new UrlInfo(jdbc.toString(), user, pass);
    }

    /**
     * Para URLs já JDBC (jdbc:postgresql://...), extrai user/password da query string,
     * remove-os da URL (não ficam no log nem conflitam com o driver) e garante sslmode.
     */
    private UrlInfo extrairCredenciaisDaQuery(String url) {
        int qIdx = url.indexOf('?');
        String base = qIdx >= 0 ? url.substring(0, qIdx) : url;
        String query = qIdx >= 0 ? url.substring(qIdx + 1) : "";

        String user = null;
        String pass = null;
        StringBuilder restantes = new StringBuilder();
        if (!query.isBlank()) {
            for (String param : query.split("&")) {
                if (param.isBlank()) {
                    continue;
                }
                String chave;
                String valor = "";
                int eq = param.indexOf('=');
                if (eq >= 0) {
                    chave = param.substring(0, eq);
                    valor = param.substring(eq + 1);
                } else {
                    chave = param;
                }
                if ("user".equals(chave)) {
                    user = decodificar(valor);
                } else if ("password".equals(chave)) {
                    pass = decodificar(valor);
                } else {
                    if (restantes.length() > 0) {
                        restantes.append('&');
                    }
                    restantes.append(param);
                }
            }
        }
        if (!restantes.toString().contains("sslmode")) {
            if (restantes.length() > 0) {
                restantes.append('&');
            }
            restantes.append("sslmode=prefer");
        }

        StringBuilder jdbc = new StringBuilder(base);
        if (restantes.length() > 0) {
            jdbc.append('?').append(restantes);
        }
        return new UrlInfo(jdbc.toString(), user, pass);
    }

    private static String decodificar(String valor) {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8);
    }

    private record UrlInfo(String jdbcUrl, String username, String password) {
    }
}
