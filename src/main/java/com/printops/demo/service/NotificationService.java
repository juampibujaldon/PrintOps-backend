// src/main/java/com/printops/demo/service/NotificationService.java
package com.printops.demo.service;

import com.printops.demo.dto.NotificationResponse;
import com.printops.demo.entity.Notification;
import com.printops.demo.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse create(String message, Long orderId) {
        Notification n = new Notification();
        n.setMessage(message);
        n.setOrderId(orderId);
        n.setRead(false);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list() {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notificación no encontrada con id " + id));
        n.setRead(true);
        return toResponse(notificationRepository.save(n));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getMessage(), n.getOrderId(), n.isRead(), n.getCreatedAt());
    }
}
