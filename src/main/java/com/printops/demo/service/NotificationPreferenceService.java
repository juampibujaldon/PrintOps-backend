// src/main/java/com/printops/demo/service/NotificationPreferenceService.java
package com.printops.demo.service;

import com.printops.demo.dto.NotificationPreferenceDTO;
import com.printops.demo.entity.NotificationPreference;
import com.printops.demo.entity.User;
import com.printops.demo.repository.NotificationPreferenceRepository;
import com.printops.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository,
                                          UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceDTO get(String email) {
        User user = resolveUser(email);
        NotificationPreference pref = repository.findById(user.getId())
                .orElseGet(() -> createDefault(user.getId()));
        return toDTO(pref);
    }

    @Transactional
    public NotificationPreferenceDTO update(String email, NotificationPreferenceDTO dto) {
        User user = resolveUser(email);
        NotificationPreference pref = repository.findById(user.getId())
                .orElseGet(() -> createDefault(user.getId()));

        pref.setMaintenanceDue(dto.maintenanceDue());
        pref.setLowStock(dto.lowStock());
        pref.setAssignedOrders(dto.assignedOrders());
        pref.setOrdersInReview(dto.ordersInReview());
        pref.setDailySummary(dto.dailySummary());
        if (dto.dailySummaryTime() != null) {
            pref.setDailySummaryTime(dto.dailySummaryTime());
        }

        repository.save(pref);
        return toDTO(pref);
    }

    private NotificationPreference createDefault(Long userId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        return repository.save(pref);
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }

    private NotificationPreferenceDTO toDTO(NotificationPreference p) {
        return new NotificationPreferenceDTO(
                p.isMaintenanceDue(), p.isLowStock(), p.isAssignedOrders(),
                p.isOrdersInReview(), p.isDailySummary(), p.getDailySummaryTime()
        );
    }
}
