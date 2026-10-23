package com.printops.demo.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

// Límite de pedidos por IP a los endpoints públicos que se pueden abusar (fuerza bruta,
// spam de emails, enumeración). Responde 429 con Retry-After. La IP es la que resolvió
// Tomcat a partir de X-Forwarded-For confiando solo en proxies internos
// (server.forward-headers-strategy=native): el cliente no la puede falsificar.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(String method, String pathPrefix, String bucket, int limit, Duration window) {
        boolean matches(HttpServletRequest req) {
            return req.getMethod().equals(method) && req.getRequestURI().startsWith(pathPrefix);
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", "login", 20, Duration.ofMinutes(5)),
            new Rule("POST", "/api/auth/google", "google", 30, Duration.ofMinutes(5)),
            new Rule("POST", "/api/auth/register", "register", 5, Duration.ofHours(1)),
            new Rule("POST", "/api/auth/forgot-password", "forgot", 5, Duration.ofHours(1)),
            new Rule("POST", "/api/auth/reset-password", "reset", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/auth/verify-email", "verify", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/auth/resend-verification", "resend", 5, Duration.ofHours(1)),
            new Rule("POST", "/api/auth/refresh", "refresh", 60, Duration.ofMinutes(5)),
            new Rule("GET", "/api/public/", "public-read", 120, Duration.ofMinutes(5)),
            new Rule("POST", "/api/public/", "public-write", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/agent/", "agent", 600, Duration.ofMinutes(5)));

    private final RateLimiter limiter;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter limiter, @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (Rule rule : RULES) {
            if (rule.matches(request)) {
                RateLimiter.Result r = limiter.hit(rule.bucket + ":" + request.getRemoteAddr(), rule.limit, rule.window);
                if (!r.allowed()) {
                    tooManyRequests(response, r.retryAfterSeconds());
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }

    static void tooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        response.getWriter().write("{\"error\":\"Demasiados intentos\",\"message\":\"Demasiados intentos. Probá de nuevo en "
                + minutes + " minuto" + (minutes == 1 ? "" : "s") + ".\"}");
    }
}
