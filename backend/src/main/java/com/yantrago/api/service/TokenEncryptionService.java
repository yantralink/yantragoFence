package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts FCM/APNs device tokens at rest.
 *
 * Per notification plan Phase 5: "Encrypt retrievable token values at rest
 * and store a SHA-256 fingerprint for uniqueness instead of the raw token."
 *
 * Uses AES-GCM-256 with a key derived from the PUSH_TOKEN_ENCRYPTION_KEY
 * environment variable. The key is hashed with SHA-256 to produce a 32-byte
 * AES key. Each encryption uses a random 12-byte IV.
 *
 * Per AGENTS.md rule 23: no secrets in source code — key from env var.
 * Per AGENTS.md rule 21: never log secrets, tokens, or passwords.
 */
@Service
public class TokenEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(TokenEncryptionService.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;
    private final boolean encryptionEnabled;

    public TokenEncryptionService(
            @Value("${push.token.encryption-key:}") String encryptionKey) {
        this.secureRandom = new SecureRandom();

        SecretKeySpec key;
        boolean enabled;
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("Push token encryption key not set (PUSH_TOKEN_ENCRYPTION_KEY). " +
                    "Token encryption is DISABLED — tokens stored in plain text. " +
                    "Set PUSH_TOKEN_ENCRYPTION_KEY in production.");
            key = null;
            enabled = false;
        } else {
            try {
                byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                        .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
                key = new SecretKeySpec(keyBytes, ALGORITHM);
                enabled = true;
                log.info("Push token encryption enabled (AES-GCM-256)");
            } catch (Exception e) {
                log.error("Failed to initialize token encryption: {}", e.getMessage());
                key = null;
                enabled = false;
            }
        }
        this.secretKey = key;
        this.encryptionEnabled = enabled;
    }

    /**
     * Returns true if encryption is enabled (key was provided).
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Encrypts a raw token value. Returns the encrypted value as a Base64 string
     * containing IV + ciphertext. If encryption is disabled, returns the raw token.
     */
    public String encrypt(String rawToken) {
        if (!encryptionEnabled || rawToken == null) {
            return rawToken;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt token: {}", e.getMessage());
            // Fail safe — return raw token rather than losing it
            return rawToken;
        }
    }

    /**
     * Decrypts an encrypted token value. Returns the raw token.
     * If encryption is disabled, returns the input as-is.
     */
    public String decrypt(String encryptedToken) {
        if (!encryptionEnabled || encryptedToken == null) {
            return encryptedToken;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedToken);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, the value might be a plain text token (pre-encryption)
            // Return as-is so the system continues to work during migration
            log.debug("Token decryption failed — treating as plain text: {}", e.getMessage());
            return encryptedToken;
        }
    }

    /**
     * Computes the SHA-256 fingerprint of a raw token.
     * Used for uniqueness lookups instead of the raw token value.
     */
    public String fingerprint(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute token fingerprint", e);
        }
    }
}
