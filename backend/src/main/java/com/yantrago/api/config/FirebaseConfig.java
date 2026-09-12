package com.yantrago.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Firebase Admin SDK initialization.
 *
 * Per notification plan Phase 5: obtain approved Firebase/APNs environment
 * configuration without exposing credentials.
 *
 * Per AGENTS.md rule 20: no secrets in source code — credentials from env vars.
 * The Firebase service account JSON is provided via the FCM_SERVICE_ACCOUNT
 * environment variable (base64-encoded or raw JSON).
 *
 * If no credentials are configured, FCM is disabled and push delivery is skipped.
 * This allows the system to run without Firebase in development/staging.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${push.provider:none}")
    private String pushProvider;

    @Value("${push.fcm.service-account:}")
    private String serviceAccountJson;

    @Value("${push.fcm.project-id:}")
    private String projectId;

    @PostConstruct
    public void initialize() {
        if (!"fcm".equals(pushProvider)) {
            log.info("Push provider is '{}' — Firebase not initialized", pushProvider);
            return;
        }

        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("Push provider is 'fcm' but no service account configured " +
                    "(push.fcm.service-account env var). FCM will not be initialized. " +
                    "Push delivery will be skipped.");
            return;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream credentialsStream = new ByteArrayInputStream(
                        serviceAccountJson.getBytes(StandardCharsets.UTF_8));

                FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentialsStream));

                if (projectId != null && !projectId.isBlank()) {
                    optionsBuilder.setProjectId(projectId);
                }

                FirebaseApp.initializeApp(optionsBuilder.build());
                log.info("Firebase Admin SDK initialized for project={}",
                        projectId != null && !projectId.isBlank() ? projectId : "(default)");
            } else {
                log.info("Firebase Admin SDK already initialized — skipping");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
            log.warn("Push delivery will be skipped. Configure push.fcm.service-account with valid credentials.");
        }
    }
}
