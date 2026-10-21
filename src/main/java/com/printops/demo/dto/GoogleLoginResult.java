package com.printops.demo.dto;

// Resultado de /api/auth/google: sesion (200) o paso de onboarding (202). Exactamente uno es no nulo.
public record GoogleLoginResult(AuthResponse auth, GoogleOnboardingResponse onboarding) {}
