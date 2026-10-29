package com.occupify.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String userIdStr = request.getHeader(HEADER_USER_ID);
        String userEmail = request.getHeader(HEADER_USER_EMAIL);
        String userRole = request.getHeader(HEADER_USER_ROLE);
        String correlationId = request.getHeader(HEADER_CORRELATION_ID);

        UUID userId = null;
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                userId = UUID.fromString(userIdStr.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);

        UserContext context = UserContext.builder()
                .userId(userId)
                .email(userEmail)
                .role(userRole)
                .correlationId(correlationId)
                .build();

        UserContextHolder.setContext(context);

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            MDC.remove("correlationId");
        }
    }
}
