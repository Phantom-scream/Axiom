package com.axiom.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-Id";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null
                || correlationId.isBlank()
                || correlationId.length() > 128
                || !correlationId.matches("[A-Za-z0-9_.:-]+")) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId); response.setHeader(HEADER, correlationId);
        try { chain.doFilter(request, response); } finally { MDC.remove("correlationId"); }
    }
}
