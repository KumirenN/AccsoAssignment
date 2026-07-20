package com.shipment.tracking.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * SHA-256 of raw webhook JSON — stored on {@code shipment_event.payload_hash} for duplicate
 * payload mismatch detection (docs/ANALYSIS.md §7.1).
 */
@Component
public class PayloadHasher {

    /**
     * Returns lowercase hex digest of the given UTF-8 string.
     */
    public String hash(String rawPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
