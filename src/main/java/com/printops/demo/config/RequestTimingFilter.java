// src/main/java/com/printops/demo/config/RequestTimingFilter.java
package com.printops.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

// Observabilidad: una línea por request con el método HTTP, la ruta, el handler
// (Controller#método) que la atendió, el status y la duración. Va a stdout y, con
// el agente de Application Insights activo, también como trace correlacionado con
// el request.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String uri = request.getRequestURI();
            String handler = handlerName(request);
            // El health check corre cada pocos segundos: a DEBUG para no llenar el log.
            if (uri.startsWith("/health")) {
                log.debug("{} {} -> {} en {} ms", request.getMethod(), uri, response.getStatus(), ms);
            } else {
                log.info("{} {} -> {} [{}] en {} ms", request.getMethod(), uri, response.getStatus(), handler, ms);
            }
        }
    }

    private static String handlerName(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod hm) {
            return hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
        }
        return "-";
    }
}
