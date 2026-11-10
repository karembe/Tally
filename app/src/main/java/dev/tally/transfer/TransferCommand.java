package dev.tally.transfer;

import dev.tally.common.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record TransferCommand(UUID fromAccount, UUID toAccount, long amount, String currency) {

    /** Fingerprint stored with the idempotency key: same key + different request = 409. */
    byte[] requestHash() {
        String canonical = String.join("|", "v1", fromAccount.toString(), toAccount.toString(),
                Long.toString(amount), currency);
        return Sha256.hash(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
