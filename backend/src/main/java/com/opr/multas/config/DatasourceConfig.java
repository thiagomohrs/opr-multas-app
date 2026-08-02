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

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        String username = properties.getUsername();
        String password = properties.getPassword();

        if (url != null) {
            if (url.startsWith("jdbc:postgresql://")) {
                UrlInfo info = extrairCredenciaisDaQuery(url);
                url = info.jdbcUrl;
                if (StringUtils.hasText(info.username)) {
                    username = info.username;
                }
                if (StringUtils.hasText(info.password)) {
                    password = info.password;
                }
            } else if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
                UrlInfo info = converterParaJdbc(url);
                url = info.jdbcUrl;
                if (StringUtils.hasText(info.username)) {
                    username = info.username;
                }
                if (StringUtils.hasText(info.password)) {
                    password = info.password;
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
        ds.setJdbcUrl(url);
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
