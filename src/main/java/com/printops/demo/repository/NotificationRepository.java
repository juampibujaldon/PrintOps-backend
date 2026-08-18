// src/main/java/com/printops/demo/repository/NotificationRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Trae las notificaciones dirigidas al usuario (recipientUserId) más las globales (null).
    List<Notification> findByRecipientUserIdOrRecipientUserIdIsNullOrderByCreatedAtDesc(Long recipientUserId);
}
