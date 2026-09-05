package com.whatsmine.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets stored at rest (integration credentials,
 * API tokens) — the Java equivalent of Laravel's `encrypted`/`encrypted:array`
 * model casts used on the PHP side for the same data.
 *
 * Static, not a Spring bean: this codebase's other JPA AttributeConverters
 * (JsonAttributeConverter, JsonListConverter, ...) are instantiated directly
 * by Hibernate without dependency injection, so this follows the same
 * pattern rather than introducing a different one just for this converter.
 *
 * Key comes from the APP_ENCRYPTION_KEY env var. Missing it in production is
 * a real risk (secrets would only have the fallback dev key), so this logs
 * loudly rather than failing silently.
 */
public final class CredentialsCipher {

    private static final Logger log = LoggerFactory.getLogger(CredentialsCipher.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final SecretKeySpec KEY = deriveKey(resolveSecret());

    private CredentialsCipher() {}

    private static String resolveSecret() {
        String secret = System.getenv("APP_ENCRYPTION_KEY");
        if (secret == null || secret.isBlank()) {
            log.warn("APP_ENCRYPTION_KEY is not set — using an insecure default key. " +
                    "Set it in production, and note that changing it later makes previously-encrypted credentials undecryptable.");
            return "dev-only-insecure-default-key-change-me";
        }
        return secret;
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), "hub-notification-credentials".getBytes(StandardCharsets.UTF_8), 65536, 256);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive credentials encryption key", e);
        }
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Credential encryption failed", e);
        }
    }

    public static String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            byte[] cipherText = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Credential decryption failed — wrong APP_ENCRYPTION_KEY, or data wasn't encrypted with this cipher: {}", e.getMessage());
            return null;
        }
    }
}
