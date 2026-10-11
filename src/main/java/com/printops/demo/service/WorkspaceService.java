// src/main/java/com/printops/demo/service/WorkspaceService.java
package com.printops.demo.service;

import com.printops.demo.dto.QuoteDefaultsDTO;
import com.printops.demo.dto.UpdateWorkspaceRequest;
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
        return toResponse(ws);
    }

    @Transactional
    public WorkspaceResponse updateMine(String email, UpdateWorkspaceRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (user.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Solo un manager puede editar el taller.");
        }
        if (user.getWorkspaceId() == null) {
            throw new NoSuchElementException("No pertenecés a ningún taller.");
        }
        Workspace ws = workspaceRepository.findById(user.getWorkspaceId())
                .orElseThrow(() -> new NoSuchElementException("Taller no encontrado"));

        ws.setName(request.name());
        ws.setPhone(request.phone());
        ws.setContactEmail(request.contactEmail());
        ws.setAddress(request.address());
        ws.setLogoUrl(request.logoUrl());
        if (request.currency() != null && !request.currency().isBlank()) {
            ws.setCurrency(request.currency());
        }

        workspaceRepository.save(ws);
        log.info("Workspace {} actualizado por {}", ws.getId(), email);
        return toResponse(ws);
    }

    // ── Quote Defaults ──

    @Transactional(readOnly = true)
    public QuoteDefaultsDTO getQuoteDefaults(String email) {
        Workspace ws = resolveWorkspace(email);
        return new QuoteDefaultsDTO(
                ws.getDefaultEnergyPriceKwh(),
                ws.getDefaultOperatorHourlyRate(),
                ws.getDefaultDesignHourlyRate(),
                ws.getDefaultMarginPercent(),
                ws.getQuoteValidityDays()
        );
    }

    @Transactional
    public QuoteDefaultsDTO updateQuoteDefaults(String email, QuoteDefaultsDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (user.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Solo un manager puede editar los valores por defecto.");
        }
        Workspace ws = resolveWorkspace(email);

        ws.setDefaultEnergyPriceKwh(dto.defaultEnergyPriceKwh());
        ws.setDefaultOperatorHourlyRate(dto.defaultOperatorHourlyRate());
        ws.setDefaultDesignHourlyRate(dto.defaultDesignHourlyRate());
        ws.setDefaultMarginPercent(dto.defaultMarginPercent());
        ws.setQuoteValidityDays(dto.quoteValidityDays());

        workspaceRepository.save(ws);
        log.info("Quote defaults actualizados por {}", email);
        return dto;
    }

    // El manager invita a un técnico por email. Devuelve el token (útil para
    // desarrollo; en producción solo se envía por email).
    @Transactional
    // Crea la invitación y manda el código por email. Devuelve si el email salió:
    // el código nunca se devuelve a la app, solo le llega al técnico.
    public boolean invite(String managerEmail, String inviteEmail) {
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

        boolean sent = false;
        try {
            emailService.sendInvitationEmail(inviteEmail, token, ws.getName());
            // Sin RESEND_API_KEY el email no sale (en dev queda en el log).
            sent = emailService.isConfigured();
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el email de invitación a {}", inviteEmail, e);
        }
        log.info("Invitación creada para {} en el taller {} (email enviado: {})", inviteEmail, ws.getName(), sent);

        return sent;
    }

    @Transactional(readOnly = true)
    public String getCanvasLayout(String email) {
        Workspace ws = resolveWorkspace(email);
        return ws.getCanvasLayout() != null ? ws.getCanvasLayout() : "{\"nodes\":[],\"edges\":[]}";
    }

    @Transactional
    public String updateCanvasLayout(String email, String layoutJson) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (user.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Solo un manager puede editar el layout del taller.");
        }
        validateCanvasLayout(layoutJson);
        Workspace ws = resolveWorkspace(email);
        ws.setCanvasLayout(layoutJson);
        workspaceRepository.save(ws);
        log.info("Layout de taller actualizado por {}", email);
        return layoutJson;
    }

    // ── Helpers ──

    // El layout se guarda tal cual y luego se devuelve a la app: se exige un objeto
    // JSON con nodes/edges y un tamaño acotado para no persistir basura.
    private void validateCanvasLayout(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank() || layoutJson.length() > 200_000) {
            throw new IllegalArgumentException("Layout de taller inválido.");
        }
        try {
            tools.jackson.databind.JsonNode node = new tools.jackson.databind.ObjectMapper().readTree(layoutJson);
            if (!node.isObject() || !node.path("nodes").isArray() || !node.path("edges").isArray()) {
                throw new IllegalArgumentException("El layout debe tener 'nodes' y 'edges'.");
            }
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("El layout no es un JSON válido.");
        }
    }

    private Workspace resolveWorkspace(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (user.getWorkspaceId() == null) {
            throw new NoSuchElementException("No pertenecés a ningún taller.");
        }
        return workspaceRepository.findById(user.getWorkspaceId())
                .orElseThrow(() -> new NoSuchElementException("Taller no encontrado"));
    }

    private WorkspaceResponse toResponse(Workspace ws) {
        return new WorkspaceResponse(
                ws.getId(), ws.getName(), ws.getPhone(), ws.getContactEmail(),
                ws.getAddress(), ws.getLogoUrl(), ws.getCurrency(),
                ws.getDefaultEnergyPriceKwh(), ws.getDefaultOperatorHourlyRate(),
                ws.getDefaultDesignHourlyRate(), ws.getDefaultMarginPercent(),
                ws.getQuoteValidityDays()
        );
    }
}
