package com.lacity.aipppc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates and hashes integration API keys. Keys look like
 * {@code aip_live_<40 hex chars>}; only the SHA-256 hash is persisted (data
 * minimization, SOW 4.4). The raw key is returned to the caller exactly once.
 */
public final class ApiKeyHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyHasher() {}

    public static String generateRawKey() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return "aip_live_" + HexFormat.of().formatHex(bytes);
    }

    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Non-secret display prefix so staff can identify a key in the UI/audit log. */
    public static String prefix(String raw) {
        int end = Math.min(raw.length(), 13);
        return raw.substring(0, end);
    }
}
