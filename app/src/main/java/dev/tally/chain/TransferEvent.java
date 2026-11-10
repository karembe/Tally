package dev.tally.chain;

import java.time.Instant;
import java.util.UUID;

/** The facts of one transfer that get hashed into the chain. */
public record TransferEvent(long seq, UUID transferId, UUID fromAccount, UUID toAccount, long amount,
                            String currency, Instant createdAt) {
}
