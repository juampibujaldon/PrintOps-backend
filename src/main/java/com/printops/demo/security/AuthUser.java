// src/main/java/com/printops/demo/security/AuthUser.java
package com.printops.demo.security;

import com.printops.demo.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Principal de Spring Security que, además del rol, expone el workspaceId
// del usuario (FIX 3). TenantContext lo lee para filtrar por tenant.
public class AuthUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Long workspaceId;
    private final String role;

    public AuthUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        this.workspaceId = user.getWorkspaceId();
        this.role = user.getRole() != null ? user.getRole().name() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }

    public Long getId() { return id; }
    public Long getWorkspaceId() { return workspaceId; }
}
