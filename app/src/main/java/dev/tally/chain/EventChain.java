package dev.tally.chain;

import dev.tally.common.Sha256;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends one event per transfer: hash = SHA-256(prev_hash || payload).
 * The single chain_head row is locked for the rest of the transaction (ADR-0007), so seq has no gaps
 * and no forks, at the cost of serializing all transfers.
 */
@Component
public class EventChain {

    private final JdbcClient jdbc;

    EventChain(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Must run inside the transfer transaction, so the event commits or rolls back with the money. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long append(UUID transferId, UUID from, UUID to, long amount, String currency, Instant createdAt) {
        Head head = jdbc.sql("select seq, hash from chain_head where id = 1 for update")
                .query((rs, row) -> new Head(rs.getLong("seq"), rs.getBytes("hash")))
                .single();

        long seq = head.seq() + 1;
        byte[] payload = CanonicalEncoder.encode(new TransferEvent(seq, transferId, from, to, amount, currency, createdAt));
        byte[] hash = Sha256.hash(head.hash(), payload);

        jdbc.sql("""
                        insert into ledger_events (seq, transfer_id, payload, prev_hash, hash, created_at)
                        values (:seq, :transferId, :payload, :prevHash, :hash, :createdAt)""")
                .param("seq", seq)
                .param("transferId", transferId)
                .param("payload", payload)
                .param("prevHash", head.hash())
                .param("hash", hash)
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
        jdbc.sql("update chain_head set seq = :seq, hash = :hash where id = 1")
                .param("seq", seq)
                .param("hash", hash)
                .update();
        return seq;
    }

    private record Head(long seq, byte[] hash) {
    }
}
