package br.gov.go.saude.athena.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(1)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.nanoTime() - startTime;
            double durationMs = duration / 1_000_000.0;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            String remoteAddr = request.getRemoteAddr();

            log.info("[{}] {} - {} ({} ms) - {}", method, uri, status, durationMs, remoteAddr);
        }
    }
}
