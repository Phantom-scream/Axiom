package com.axiom.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(10)
public class OperatorApiKeyFilter extends OncePerRequestFilter {
    private final ApiSecurityProperties properties;
    public OperatorApiKeyFilter(ApiSecurityProperties properties) { this.properties = properties; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicPath = path.equals("/api/v1/webhooks/github") || path.equals("/api/v1/health")
                || path.equals("/actuator/health") || path.equals("/actuator/health/liveness") || path.equals("/actuator/health/readiness");
        if (properties.enabledValue() && !publicPath) {
            String provided = request.getHeader("X-Axiom-Api-Key");
            if (provided == null || provided.length() > 4096 || !MessageDigest.isEqual(
                    properties.key().getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"OPERATOR_AUTH_REQUIRED\",\"message\":\"A valid operator API key is required.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
