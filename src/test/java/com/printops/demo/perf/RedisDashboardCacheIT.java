package com.printops.demo.perf;

import com.printops.demo.config.RedisCacheConfig;
import com.printops.demo.dto.DashboardMetricsDTO;
import com.printops.demo.dto.LowStockPartDTO;
import com.printops.demo.dto.OrderTypeBreakdownDTO;
import com.printops.demo.dto.PrinterAvailabilityDTO;
import com.printops.demo.service.DashboardCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.Socket;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Cache del dashboard contra un Redis real (REDIS_TEST_PORT, default 6379). Sin Redis
// accesible el test se saltea: el resto de los IT usan Caffeine (perfil test).
@SpringBootTest(
        classes = RedisDashboardCacheIT.TestConfig.class,
        properties = {
                "spring.cache.type=redis",
                "spring.data.redis.url=redis://localhost:${REDIS_TEST_PORT:6379}",
        })
class RedisDashboardCacheIT {

    @BeforeAll
    static void requireRedis() {
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        boolean up;
        try (Socket ignored = new Socket("localhost", port)) {
            up = true;
        } catch (Exception e) {
            up = false;
        }
        assumeTrue(up, "Redis no disponible en localhost:" + port);
    }

    @Configuration
    @EnableCaching
    @ImportAutoConfiguration({DataRedisAutoConfiguration.class, CacheAutoConfiguration.class})
    @org.springframework.context.annotation.Import(RedisCacheConfig.class)
    static class TestConfig {
        @Bean
        MetricsStub metricsStub() {
            return new MetricsStub();
        }
    }

    // Bean con proxy de cache: el contador se lee por metodos (los campos del proxy CGLIB son null).
    static class MetricsStub {
        private final AtomicInteger calls = new AtomicInteger();

        public int calls() {
            return calls.get();
        }

        public void reset() {
            calls.set(0);
        }

        @Cacheable(cacheNames = DashboardCache.NAME, key = "#key")
        public DashboardMetricsDTO metrics(String key) {
            calls.incrementAndGet();
            return sample();
        }
    }

    static DashboardMetricsDTO sample() {
        return new DashboardMetricsDTO(
                97.5, List.of(new PrinterAvailabilityDTO(1L, "Prusa", 97.5)),
                120.0, 80.0, 40.0, List.of(), List.of(),
                10, 3, 7, new OrderTypeBreakdownDTO(4, 5, 1), 12.5, 1,
                List.of(), List.of(new LowStockPartDTO(2L, "Boquilla", "N-04", 1, 3)),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22));
    }

    @Autowired MetricsStub stub;
    @Autowired CacheManager cacheManager;
    @Autowired StringRedisTemplate redis;

    @Test
    void cachesDashboardAsTypedJsonWithTtl_andClearRemovesEntries() {
        Cache cache = cacheManager.getCache(DashboardCache.NAME);
        cache.clear();
        stub.reset();

        DashboardMetricsDTO first = stub.metrics("42:2026-10-01:2026-10-22");
        DashboardMetricsDTO second = stub.metrics("42:2026-10-01:2026-10-22");

        assertThat(stub.calls()).isEqualTo(1);
        assertThat(second).isEqualTo(first).isEqualTo(sample());

        String redisKey = "printops:" + DashboardCache.NAME + "::42:2026-10-01:2026-10-22";
        assertThat(redis.opsForValue().get(redisKey)).contains("\"globalAvailabilityPercent\"");
        Long ttl = redis.getExpire(redisKey);
        assertThat(ttl).isBetween(1L, 300L);

        cache.invalidate();
        assertThat(redis.hasKey(redisKey)).isFalse();
        stub.metrics("42:2026-10-01:2026-10-22");
        assertThat(stub.calls()).isEqualTo(2);
    }
}
