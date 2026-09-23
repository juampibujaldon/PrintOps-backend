// src/main/java/com/printops/demo/service/FcmService.java
package com.printops.demo.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;

// Envío de notificaciones push con Firebase Cloud Messaging (US-05).
//
// Se inicializa de forma lazy a partir de la ruta del service account JSON de
// Firebase (app.firebase.service-account-path). Si no está configurada, el
// servicio queda en modo no-op: registra un warning y no envía push, pero el
// resto de la app (notificaciones in-app) sigue funcionando.
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private final FirebaseMessaging messaging;

    public FcmService(@Value("${app.firebase.service-account-path:}") String serviceAccountPath) {
        this.messaging = initMessaging(serviceAccountPath);
    }

    private FirebaseMessaging initMessaging(String serviceAccountPath) {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            log.warn("FCM deshabilitado: no se configuró app.firebase.service-account-path. Sin push.");
            return null;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath)))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            log.error("No se pudo inicializar Firebase Admin (service account). Push deshabilitado.", e);
            return null;
        }
    }

    /**
     * Envía un push al token indicado. Silencioso si FCM no está disponible
     * o si el token es nulo/vacío.
     */
    public void sendPush(String fcmToken, String title, String body, Long orderId) {
        if (messaging == null || fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("orderId", orderId != null ? String.valueOf(orderId) : "")
                    .build();
            messaging.send(message);
        } catch (Exception e) {
            // Un token inválido/vencido no debe tumbar la transacción de estado.
            log.warn("Error enviando push FCM para la orden {}", orderId, e);
        }
    }
}
