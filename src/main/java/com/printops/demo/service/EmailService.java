// src/main/java/com/printops/demo/service/EmailService.java
package com.printops.demo.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// Servicio de envío de emails usando la API REST de Resend.
// No usa SDK externo: llama a POST https://api.resend.com/emails con HttpClient del JDK
// y arma el JSON a mano para no depender de la versión de Jackson del proyecto.
@Service
public class EmailService {

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";
    private static final String TEST_SENDER_DOMAIN = "resend.dev";
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final HttpClient httpClient;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from}")
    private String from;

    @Value("${resend.log-when-disabled:true}")
    private boolean logWhenDisabled;

    public EmailService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    // Deja claro al arrancar cómo se van a comportar los emails.
    @PostConstruct
    void logConfiguration() {
        if (!isConfigured()) {
            log.warn("Resend NO configurado (RESEND_API_KEY vacía): los emails no se envían{}.",
                    logWhenDisabled ? "; su contenido se escribe en este log" : "");
        } else if (from != null && from.contains(TEST_SENDER_DOMAIN)) {
            log.warn("Resend en modo prueba (remitente {}): solo entrega al email de tu cuenta de Resend. "
                    + "Para enviar a cualquier destinatario verificá un dominio y configurá RESEND_FROM.", from);
        } else {
            log.info("Resend configurado. Remitente: {}", from);
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public void sendVerificationEmail(String to, String code) {
        String subject = "Verificá tu email en PrintOps";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px">
                  <h2 style="color:#283618">Bienvenido a PrintOps</h2>
                  <p>Para activar tu cuenta, ingresá el siguiente código en la aplicación:</p>
                  <p style="margin:24px 0;font-size:32px;font-weight:bold;letter-spacing:6px;text-align:center;color:#283618">%s</p>
                  <p style="color:#666;font-size:12px">
                    Si no creaste una cuenta en PrintOps, ignorá este mensaje. El código expira en 24 horas.
                  </p>
                </div>
                """.formatted(code);
        send(to, subject, html);
    }

    // Invitación a un técnico a unirse al taller (contiene el código de invitación).
    public void sendInvitationEmail(String to, String token, String workspaceName) {
        String subject = "Invitación a " + (workspaceName != null ? workspaceName : "PrintOps");
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px">
                  <h2 style="color:#283618">Te invitaron a un taller de PrintOps</h2>
                  <p>Fuiste invitado a unirte al taller <b>%s</b> como técnico.</p>
                  <p>Usá este código al registrarte en la app:</p>
                  <p style="margin:24px 0;font-size:22px;font-weight:bold;letter-spacing:2px">%s</p>
                  <p style="color:#666;font-size:12px">El código expira en 7 días. Si no esperabas esta invitación, ignorá este mensaje.</p>
                </div>
                """.formatted(escapeHtml(workspaceName != null ? workspaceName : "PrintOps"), escapeHtml(token));
        send(to, subject, html);
    }

    // Email para restablecer la contraseña. Solo lleva el código: la app no tiene
    // deep linking y los clientes de mail bloquean los enlaces printops://.
    public void sendPasswordResetEmail(String to, String token) {
        String subject = "Restablecé tu contraseña en PrintOps";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px">
                  <h2 style="color:#283618">Restablecer contraseña</h2>
                  <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta de PrintOps.</p>
                  <p>Copiá este código y pegalo en la app, en <b>Recuperar contraseña</b>:</p>
                  <p style="margin:24px 0;padding:16px;background:#f2f2f7;border-radius:8px;font-family:monospace;font-size:15px;font-weight:bold;text-align:center;word-break:break-all">%s</p>
                  <p style="color:#666;font-size:12px">
                    Si no solicitaste este cambio, ignorá este mensaje. El código expira en 1 hora.
                  </p>
                </div>
                """.formatted(escapeHtml(token));
        send(to, subject, html);
    }

    public void send(String to, String subject, String html) {
        if (!isConfigured()) {
            // Modo desarrollo: sin key no se envía, pero se deja el contenido en el
            // log para poder seguir el flujo (código de invitación / reset).
            if (logWhenDisabled) {
                log.warn("[EMAIL NO ENVIADO — Resend sin configurar] Para: {} | Asunto: {} | Contenido: {}",
                        to, subject, toPlainText(html));
                return;
            }
            throw new IllegalStateException("RESEND_API_KEY no está configurada.");
        }

        String body = buildJson(from, to, subject, html);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_EMAILS_URL))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String hint = response.statusCode() == 403 && from != null && from.contains(TEST_SENDER_DOMAIN)
                        ? " (con el remitente de prueba de Resend solo se puede enviar al email de tu cuenta)"
                        : "";
                throw new RuntimeException("Resend devolvió " + response.statusCode() + hint + ": " + response.body());
            }
            log.info("Email enviado a {} ({})", to, subject);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo enviar el email.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("El envío del email fue interrumpido.", e);
        }
    }

    private String toPlainText(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    // Arma el payload JSON de Resend escapando correctamente los strings.
    private String buildJson(String from, String to, String subject, String html) {
        return "{"
                + "\"from\":\"" + escape(from) + "\","
                + "\"to\":[\"" + escape(to) + "\"],"
                + "\"subject\":\"" + escape(subject) + "\","
                + "\"html\":\"" + escape(html) + "\""
                + "}";
    }

    // El nombre del taller lo escribe el usuario: se escapa antes de meterlo en el HTML.
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
