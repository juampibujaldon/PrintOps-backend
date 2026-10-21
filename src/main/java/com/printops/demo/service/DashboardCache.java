package com.printops.demo.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Invalidacion de la cache del dashboard (US-08). La clave incluye workspace y rango
// de fechas, asi que se vacia entera. Se hace DESPUES del commit: con un @CacheEvict
// dentro de la transaccion, un GET concurrente podia volver a cargar la cache con
// datos anteriores al commit y dejarla vieja hasta 5 minutos.
@Component
public class DashboardCache {

    public static final String NAME = "dashboard-metrics";

    private final ApplicationEventPublisher publisher;
    private final CacheManager cacheManager;

    public DashboardCache(ApplicationEventPublisher publisher, CacheManager cacheManager) {
        this.publisher = publisher;
        this.cacheManager = cacheManager;
    }

    private record Invalidated() {}

    // Llamar desde cualquier escritura que cambie el contenido del dashboard. Dentro de una
    // transaccion se difiere hasta el commit (y se descarta si hay rollback); sin transaccion
    // se ejecuta de inmediato.
    public void invalidate() {
        publisher.publishEvent(new Invalidated());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void evict(Invalidated event) {
        Cache cache = cacheManager.getCache(NAME);
        if (cache != null) {
            cache.clear();
        }
    }
}
