package dev.tally.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256 {

    private Sha256() {
    }

    public static byte[] hash(byte[]... parts) {
        MessageDigest digest = newDigest();
        for (byte[] part : parts) {
            digest.update(part);
        }
        return digest.digest();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JVM", e);
        }
    }
}
