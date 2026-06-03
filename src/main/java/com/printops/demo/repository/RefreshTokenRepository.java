// src/main/java/com/printops/demo/repository/RefreshTokenRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.RefreshToken;
import com.printops.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserAndDeviceIdAndRevokedFalse(User user, String deviceId);

    List<RefreshToken> findByUserAndRevokedFalseAndExpiresAtAfter(User user, Instant now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user AND r.deviceId = :deviceId")
    void revokeByUserAndDeviceId(User user, String deviceId);

    // Limpieza de tokens expirados
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    void deleteExpired(Instant now);
}
