package com.commercex.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook signature: header {@code t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>}.
 * The timestamp is signed, so an old captured request cannot be replayed after the tolerance window.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-CommerceX-Signature";

    private WebhookSignature() {
    }

    public static String sign(String secret, long unixSeconds, String body) {
        return "t=" + unixSeconds + ",v1=" + hmac(secret, unixSeconds + "." + body);
    }

    public static boolean verify(String secret, String header, String body, Instant now, Duration tolerance) {
        if (header == null || body == null) {
            return false;
        }
        Long timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                signature = kv[1];
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - timestamp) > tolerance.toSeconds()) {
            return false;
        }
        byte[] expected = hmac(secret, timestamp + "." + body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII)); // constant time
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
