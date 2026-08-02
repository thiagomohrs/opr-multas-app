package com.opr.multas.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class DatasourceConfig {

    /**
     * Aceita URLs no formato nativo do Supabase (ex.: postgresql://user:pass@host:5432/db)
     * e converte para uma URL JDBC válida (jdbc:postgresql://...). Adiciona sslmode=prefer
     * quando não especificado: usa SSL quando o servidor suporta (Supabase) e cai para
     * texto plano caso contrário (Postgres local).
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

        if (url != null && (url.startsWith("postgresql://") || url.startsWith("postgres://"))) {
            UrlInfo info = converterParaJdbc(url);
            url = info.jdbcUrl;
            if (!StringUtils.hasText(username)) {
                username = info.username;
            }
            if (!StringUtils.hasText(password)) {
                password = info.password;
            }
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
                user = userinfo.substring(0, idx);
                pass = userinfo.substring(idx + 1);
            } else {
                user = userinfo;
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

    private record UrlInfo(String jdbcUrl, String username, String password) {
    }
}
