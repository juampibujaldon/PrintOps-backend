// src/main/java/com/printops/demo/security/TenantContext.java
package com.printops.demo.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Utilidad que extrae el workspaceId del usuario autenticado (FIX 3).
// Devuelve null cuando no hay contexto (ej. scheduler) o el usuario no
// tiene workspace asignado.
public final class TenantContext {

    private TenantContext() {
    }

    public static Long getCurrentWorkspaceId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser au) {
            return au.getWorkspaceId();
        }
        return null;
    }
}
