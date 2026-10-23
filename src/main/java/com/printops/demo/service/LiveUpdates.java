package com.printops.demo.service;

import com.printops.demo.entity.User;
import com.printops.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Mensajes en vivo por WebSocket a /queue/user-{id} (la única suscripción permitida).
// Cada mensaje lleva "type" para que el front distinga notificaciones (toast) de datos
// en vivo (telemetría, cola de trabajos). Dentro de una transacción se envía tras el
// commit: el cliente que reacciona recargando ve los datos ya guardados.
@Component
public class LiveUpdates {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);

    private final SimpMessagingTemplate messaging;
    private final UserRepository userRepository;

    public LiveUpdates(SimpMessagingTemplate messaging, UserRepository userRepository) {
        this.messaging = messaging;
        this.userRepository = userRepository;
    }

    public void toUser(Long userId, String type, Map<String, ?> payload) {
        if (userId == null) return;
        Map<String, Object> body = new HashMap<>(payload);
        body.put("type", type);
        afterCommit(() -> send(userId, body));
    }

    public void toWorkspace(Long workspaceId, String type, Map<String, ?> payload) {
        if (workspaceId == null) return;
        List<Long> ids = userRepository.findByWorkspaceId(workspaceId).stream().map(User::getId).toList();
        Map<String, Object> body = new HashMap<>(payload);
        body.put("type", type);
        afterCommit(() -> ids.forEach(id -> send(id, body)));
    }

    private void send(Long userId, Map<String, Object> body) {
        try {
            messaging.convertAndSend("/queue/user-" + userId, (Object) body);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el mensaje en vivo a {}: {}", userId, e.getMessage());
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
