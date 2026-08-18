// src/main/java/com/printops/demo/service/NotificationService.java
package com.printops.demo.service;

import com.printops.demo.dto.NotificationResponse;
import com.printops.demo.entity.Notification;
import com.printops.demo.entity.User;
import com.printops.demo.repository.NotificationRepository;
import com.printops.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // Crea una notificación dirigida a un usuario (o global si recipientUserId es null).
    @Transactional
    public NotificationResponse createFor(Long recipientUserId, String type, String message, Long orderId) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setType(type);
        n.setMessage(message);
        n.setOrderId(orderId);
        n.setRead(false);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        return notificationRepository.findByRecipientUserIdOrRecipientUserIdIsNullOrderByCreatedAtDesc(user.getId()).stream()
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
        return new NotificationResponse(
                n.getId(), n.getMessage(), n.getOrderId(),
                n.getRecipientUserId(), n.getType(), n.isRead(), n.getCreatedAt());
    }
}
