package dev.tally.web;

import dev.tally.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Static admin token for funding until Vault arrives (phase 7). */
@Component
public class AdminTokenGuard {

    private final byte[] expected;

    AdminTokenGuard(@Value("${tally.admin-token:}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    public void check(String presented) {
        boolean valid = expected.length > 0
                && presented != null
                // Constant-time compare so response timing does not leak the token byte by byte.
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw ApiException.unauthorized("missing or invalid admin token");
        }
    }
}
