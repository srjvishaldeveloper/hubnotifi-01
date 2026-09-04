package com.whatsmine.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    /**
     * Verify a 6-digit TOTP code against a Base32 secret.
     */
    public boolean verifyCode(String base32Secret, String codeStr) {
        if (base32Secret == null || codeStr == null || codeStr.trim().length() != DIGITS) {
            return false;
        }

        try {
            int inputCode = Integer.parseInt(codeStr.trim());
            byte[] key = decodeBase32(base32Secret.trim());
            long currentTimeSecond = System.currentTimeMillis() / 1000L;

            // Allow window of +-1 time step (30 seconds) for clock drift
            for (int i = -1; i <= 1; i++) {
                long timeStep = (currentTimeSecond / TIME_STEP_SECONDS) + i;
                if (generateTotp(key, timeStep) == inputCode) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    private int generateTotp(byte[] key, long timeStep) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);

        return binary % (int) Math.pow(10, DIGITS);
    }

    private byte[] decodeBase32(String base32) {
        String base32Upper = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] bytes = new byte[base32Upper.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;

        for (char c : base32Upper.toCharArray()) {
            int val;
            if (c >= 'A' && c <= 'Z') {
                val = c - 'A';
            } else if (c >= '2' && c <= '7') {
                val = c - '2' + 26;
            } else {
                continue;
            }

            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                bytes[count++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }

        return bytes;
    }
}
