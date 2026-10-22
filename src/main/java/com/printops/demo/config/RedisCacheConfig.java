package com.printops.demo.config;

import com.printops.demo.dto.DashboardMetricsDTO;
import com.printops.demo.service.DashboardCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

// Cache en Redis (spring.cache.type=redis, el default fuera de dev/test).
// - Valores en JSON tipado: los DTO son records sin Serializable, y un tipo fijo evita
//   la deserializacion polimorfica de un serializer generico.
// - clear() (lo usa DashboardCache) recorre las claves con SCAN en vez de KEYS, que
//   bloquea Redis mientras recorre todo el keyspace.
// - Si Redis no responde, el error se loguea y el metodo se ejecuta sin cache en vez de
//   devolver 500 (con el timeout corto de spring.data.redis.timeout).
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig implements CachingConfigurer {

    static final Duration DASHBOARD_TTL = Duration.ofMinutes(5);
    static final String KEY_PREFIX = "printops:";

    @Bean
    RedisCacheManagerBuilderCustomizer printopsRedisCaches(RedisConnectionFactory connectionFactory) {
        return builder -> builder
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500)))
                .withCacheConfiguration(DashboardCache.NAME, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(DASHBOARD_TTL)
                        .prefixCacheNameWith(KEY_PREFIX)
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(DashboardMetricsDTO.class))));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}
