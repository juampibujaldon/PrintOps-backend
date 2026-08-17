// src/main/java/com/printops/demo/service/EmailService.java
package com.printops.demo.service;

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

    private final HttpClient httpClient;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from}")
    private String from;

    public EmailService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendVerificationEmail(String to, String verificationUrl) {
        String subject = "Verificá tu email en PrintOps";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px">
                  <h2 style="color:#283618">Bienvenido a PrintOps</h2>
                  <p>Para activar tu cuenta, confirmá tu dirección de email haciendo clic en el siguiente enlace:</p>
                  <p style="margin:24px 0">
                    <a href="%s"
                       style="background-color:#283618;color:#ffffff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold">
                       Verificar mi email
                    </a>
                  </p>
                  <p style="color:#666;font-size:12px">
                    Si no creaste una cuenta en PrintOps, ignorá este mensaje. El enlace expira en 24 horas.
                  </p>
                </div>
                """.formatted(verificationUrl);
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
                """.formatted(workspaceName != null ? workspaceName : "PrintOps", token);
        send(to, subject, html);
    }

    public void send(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY no está configurada.");
        }

        String body = buildJson(from, to, subject, html);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_EMAILS_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Resend devolvió " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo enviar el email.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("El envío del email fue interrumpido.", e);
        }
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

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
