// src/main/java/com/printops/demo/repository/UserRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Role;
import com.printops.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String verificationToken);
    Optional<User> findByResetToken(String resetToken);

    // US-06: técnicos a los que se notifica al dispararse una regla.
    List<User> findByRole(Role role);
}
