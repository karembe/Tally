package dev.tally.account;

import java.time.Instant;
import java.util.UUID;

/** Balance is in minor units (cents); money is never a float. */
public record Account(UUID id, String ownerName, String currency, AccountKind kind, long balance, Instant createdAt) {
}
