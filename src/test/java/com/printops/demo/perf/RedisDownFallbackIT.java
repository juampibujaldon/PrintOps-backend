package com.printops.demo.perf;

import com.printops.demo.dto.DashboardMetricsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// Con Redis caido (puerto cerrado) el metodo cacheado se ejecuta igual: el
// CacheErrorHandler de RedisCacheConfig loguea y sigue, no propaga la excepcion.
@SpringBootTest(
        classes = RedisDashboardCacheIT.TestConfig.class,
        properties = {
                "spring.cache.type=redis",
                "spring.data.redis.url=redis://localhost:1",
                "spring.data.redis.timeout=500ms",
                "spring.data.redis.connect-timeout=500ms",
        })
class RedisDownFallbackIT {

    @Autowired RedisDashboardCacheIT.MetricsStub stub;

    @Test
    void cachedMethodStillAnswersWhenRedisIsDown() {
        stub.reset();
        DashboardMetricsDTO a = stub.metrics("1:2026-10-01:2026-10-22");
        DashboardMetricsDTO b = stub.metrics("1:2026-10-01:2026-10-22");

        assertThat(a).isEqualTo(RedisDashboardCacheIT.sample());
        assertThat(b).isEqualTo(a);
        assertThat(stub.calls()).isEqualTo(2);
    }
}
