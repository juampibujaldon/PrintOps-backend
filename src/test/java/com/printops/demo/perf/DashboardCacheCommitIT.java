package com.printops.demo.perf;

import com.printops.demo.dto.MetricsFilters;
import com.printops.demo.service.DashboardCache;
import com.printops.demo.service.GlobalMetricsService;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// La cache solo se vacia cuando la transaccion que escribio ya hizo commit.
class DashboardCacheCommitIT extends IntegrationTestBase {

    @Autowired DashboardCache dashboardCache;
    @Autowired GlobalMetricsService metricsService;
    @Autowired CacheManager cacheManager;

    private static final MetricsFilters PERIOD = new MetricsFilters(LocalDate.now().minusDays(3), LocalDate.now());

    private String key(World w) {
        return w.workspaceId + ":" + PERIOD.from() + ":" + PERIOD.to();
    }

    private Cache primedCache(World w) {
        loginAs(w.manager);
        metricsService.getDashboardMetrics(PERIOD);
        Cache cache = cacheManager.getCache("dashboard-metrics");
        assertThat(cache.get(key(w))).as("cache primed").isNotNull();
        return cache;
    }

    @Test
    void evictionHappensAfterCommit_notBefore() {
        World w = newWorld();
        Cache cache = primedCache(w);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            dashboardCache.invalidate();
            assertThat(cache.get(key(w))).as("todavia cacheado antes del commit").isNotNull();
        });

        assertThat(cache.get(key(w))).as("vaciado tras el commit").isNull();
    }

    @Test
    void rolledBackTransactionDoesNotEvict() {
        World w = newWorld();
        Cache cache = primedCache(w);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            dashboardCache.invalidate();
            status.setRollbackOnly();
        });

        assertThat(cache.get(key(w))).isNotNull();
    }

    @Test
    void evictionWithoutTransactionIsImmediate() {
        World w = newWorld();
        Cache cache = primedCache(w);

        dashboardCache.invalidate();

        assertThat(cache.get(key(w))).isNull();
    }
}
