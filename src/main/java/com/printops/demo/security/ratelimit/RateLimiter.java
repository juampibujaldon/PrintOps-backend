package com.printops.demo.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Contadores de ventana fija compartidos entre réplicas (Redis INCR + EXPIRE). Si no hay
// Redis (dev/test) o Redis falla, usa memoria local: el límite pasa a ser por réplica,
// pero nunca se bloquea un login porque Redis esté caído.
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String PREFIX = "printops:rl:";
    private static final int MAX_LOCAL_KEYS = 50_000;

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<String, Window> local = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(ObjectProvider<StringRedisTemplate> redis,
                       @Value("${spring.cache.type:caffeine}") String cacheType) {
        this(("redis".equalsIgnoreCase(cacheType)) ? redis.getIfAvailable() : null, Clock.systemUTC());
    }

    RateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public record Result(boolean allowed, long count, long retryAfterSeconds) {
    }

    // Suma un evento y dice si sigue dentro del límite.
    public Result hit(String key, int limit, Duration window) {
        Counter c = increment(key, window);
        return new Result(c.count <= limit, c.count, c.count <= limit ? 0 : c.ttlSeconds);
    }

    // Consulta sin sumar (p. ej. "¿esta IP ya superó los intentos fallidos?").
    public Result peek(String key, int limit) {
        Counter c = current(key);
        return new Result(c.count < limit, c.count, c.count < limit ? 0 : c.ttlSeconds);
    }

    public void reset(String key) {
        local.remove(key);
        if (redis != null) {
            try {
                redis.delete(PREFIX + key);
            } catch (RuntimeException e) {
                log.debug("Rate limit: Redis no disponible al resetear {}", key);
            }
        }
    }

    private record Counter(long count, long ttlSeconds) {
    }

    private Counter increment(String key, Duration window) {
        if (redis != null) {
            try {
                String k = PREFIX + key;
                Long count = redis.opsForValue().increment(k);
                long c = count != null ? count : 1;
                if (c == 1) {
                    redis.expire(k, window);
                }
                Long ttl = redis.getExpire(k);
                if (ttl == null || ttl < 0) {
                    // Clave sin TTL (p. ej. se cortó entre INCR y EXPIRE): no dejarla eterna.
                    redis.expire(k, window);
                    ttl = window.toSeconds();
                }
                return new Counter(c, ttl);
            } catch (RuntimeException e) {
                log.warn("Rate limit: Redis no disponible, se usa el contador local ({})", e.getMessage());
            }
        }
        return localIncrement(key, window);
    }

    private Counter current(String key) {
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(PREFIX + key);
                if (v == null) return new Counter(0, 0);
                Long ttl = redis.getExpire(PREFIX + key);
                return new Counter(Long.parseLong(v), ttl != null && ttl > 0 ? ttl : 0);
            } catch (RuntimeException e) {
                log.warn("Rate limit: Redis no disponible, se usa el contador local ({})", e.getMessage());
            }
        }
        Window w = local.get(key);
        long now = clock.millis();
        if (w == null || w.endsAt <= now) return new Counter(0, 0);
        return new Counter(w.count, Math.max(1, (w.endsAt - now + 999) / 1000));
    }

    private Counter localIncrement(String key, Duration window) {
        long now = clock.millis();
        if (local.size() > MAX_LOCAL_KEYS) {
            local.values().removeIf(w -> w.endsAt <= now);
        }
        Window w = local.compute(key, (k, old) ->
                old == null || old.endsAt <= now ? new Window(1, now + window.toMillis()) : new Window(old.count + 1, old.endsAt));
        return new Counter(w.count, Math.max(1, (w.endsAt - now + 999) / 1000));
    }

    private record Window(long count, long endsAt) {
    }
}
