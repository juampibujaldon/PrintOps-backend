// src/main/java/com/printops/demo/service/WorkspaceService.java
package com.printops.demo.service;

import com.printops.demo.dto.WorkspaceResponse;
import com.printops.demo.entity.Role;
import com.printops.demo.entity.User;
import com.printops.demo.entity.Workspace;
import com.printops.demo.entity.WorkspaceInvite;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.repository.WorkspaceInviteRepository;
import com.printops.demo.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
    private static final long INVITE_TOKEN_TTL_SECONDS = 7 * 24 * 3600L;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceInviteRepository inviteRepository,
                            UserRepository userRepository,
                            EmailService emailService) {
        this.workspaceRepository = workspaceRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getMine(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (user.getWorkspaceId() == null) {
            throw new NoSuchElementException("No pertenecés a ningún taller.");
        }
        Workspace ws = workspaceRepository.findById(user.getWorkspaceId())
                .orElseThrow(() -> new NoSuchElementException("Taller no encontrado"));
        return new WorkspaceResponse(ws.getId(), ws.getName());
    }

    // El manager invita a un técnico por email. Devuelve el token (útil para
    // desarrollo; en producción solo se envía por email).
    @Transactional
    public String invite(String managerEmail, String inviteEmail) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        if (manager.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Solo un manager puede invitar técnicos.");
        }
        if (manager.getWorkspaceId() == null) {
            throw new IllegalArgumentException("No tenés un taller asociado.");
        }
        if (userRepository.findByEmail(inviteEmail).isPresent()) {
            throw new IllegalArgumentException("Ese email ya está registrado.");
        }

        Workspace ws = workspaceRepository.findById(manager.getWorkspaceId())
                .orElseThrow(() -> new NoSuchElementException("Taller no encontrado"));

        String token = UUID.randomUUID().toString();
        WorkspaceInvite invite = new WorkspaceInvite();
        invite.setWorkspaceId(ws.getId());
        invite.setEmail(inviteEmail);
        invite.setToken(token);
        invite.setExpiresAt(Instant.now().plusSeconds(INVITE_TOKEN_TTL_SECONDS));
        invite.setAccepted(false);
        inviteRepository.save(invite);

        emailService.sendInvitationEmail(inviteEmail, token, ws.getName());
        log.info("Invitación enviada a {} para el taller {} (token={})", inviteEmail, ws.getName(), token);

        return token;
    }
}
