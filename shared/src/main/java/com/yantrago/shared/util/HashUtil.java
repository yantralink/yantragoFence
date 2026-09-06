package com.yantrago.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing utility shared by backend and gateway. Used for
 * deterministic hashing of identifiers (e.g. IMEI digests, request
 * fingerprints). Not for password storage — passwords use BCrypt in
 * the backend's AuthService.
 */
public final class HashUtil {

    private HashUtil() {
        throw new UnsupportedOperationException("Utility only");
    }

    public static String sha256Hex(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
