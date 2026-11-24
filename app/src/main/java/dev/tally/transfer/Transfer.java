package dev.tally.transfer;

import java.time.Instant;
import java.util.UUID;

public record Transfer(UUID id, UUID fromAccount, UUID toAccount, long amount, String currency, String status,
                       long eventSeq, Instant createdAt) {

    public static final String COMMITTED = "COMMITTED";
}
