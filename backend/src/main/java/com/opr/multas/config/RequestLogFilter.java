package com.opr.multas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long inicio = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.error("[REQ] {} {} -> ERRO: {}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        } finally {
            log.info("[REQ] {} {} ({}ms) -> {}", request.getMethod(), request.getRequestURI(),
                System.currentTimeMillis() - inicio, response.getStatus());
        }
    }
}
