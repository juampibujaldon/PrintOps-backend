// src/main/java/com/printops/demo/dto/AuthResponse.java
package com.printops.demo.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserInfo user
) {
    public record UserInfo(Long id, String email, String role) {}
}
