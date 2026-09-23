// src/main/java/com/printops/demo/service/UserDetailsServiceImpl.java
package com.printops.demo.service;

import com.printops.demo.entity.User;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.AuthUser;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        // FIX 3: el principal expone workspaceId para el filtrado multi-tenant.
        return new AuthUser(user);
    }
}
