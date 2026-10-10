package com.printops.demo.config;

import com.printops.demo.repository.TokenBlacklistRepository;
import com.printops.demo.security.AuthUser;
import com.printops.demo.service.JwtService;
import com.printops.demo.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistRepository blacklistRepository;

    public WebSocketConfig(JwtService jwtService, UserDetailsServiceImpl userDetailsService,
                           TokenBlacklistRepository blacklistRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.blacklistRepository = blacklistRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefijo para enviar mensajes desde el servidor al cliente
        config.enableSimpleBroker("/topic", "/queue");
        // Prefijo para mensajes enviados desde el cliente al servidor (si es necesario)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint donde el frontend se conectará al WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    // El handshake HTTP de /ws es público, así que la autenticación se hace en el
    // frame CONNECT (header Authorization: Bearer <jwt>). Además, cada usuario solo
    // puede suscribirse a su propia cola: antes cualquiera podía leer /queue/user-{id}.
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscription(accessor.getUser(), accessor.getDestination());
                }
                return message;
            }
        });
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Token ausente");
        }
        String token = header.substring(7);
        if (!jwtService.validateToken(token) || blacklistRepository.existsByTokenHash(sha256(token))) {
            throw new MessagingException("Token inválido o revocado");
        }
        try {
            AuthUser user = (AuthUser) userDetailsService.loadUserByUsername(jwtService.extractEmail(token));
            if (!user.isEnabled()) {
                throw new MessagingException("Usuario deshabilitado");
            }
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        } catch (UsernameNotFoundException e) {
            throw new MessagingException("Usuario inexistente");
        }
    }

    private void authorizeSubscription(Principal principal, String destination) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new MessagingException("No autenticado");
        }
        if (destination == null || !destination.equals("/queue/user-" + user.getId())) {
            throw new MessagingException("Suscripción no permitida");
        }
    }

    private static String sha256(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
