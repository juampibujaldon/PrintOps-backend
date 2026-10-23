// src/main/java/com/printops/demo/dto/AuthResponse.java
package com.printops.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// refreshToken viaja en una cookie httpOnly (AuthController), no en el JSON: el campo queda
// null en la respuesta. refreshExpiresAt/persistentRefresh solo sirven para armar la cookie.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserInfo user,
    @JsonIgnore Instant refreshExpiresAt,
    @JsonIgnore boolean persistentRefresh
) {
    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(accessToken, null, expiresIn, user, refreshExpiresAt, persistentRefresh);
    }

    public record UserInfo(Long id, String email, String role, Long workspaceId) {}
}
