package com.printops.demo.service;

// Datos verificados de un ID token de Google.
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {}
