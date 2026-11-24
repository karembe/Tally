package dev.tally.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyRepository {

    private final JdbcClient jdbc;

    IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns true if this request now owns the key. A concurrent request with the same key blocks on the
     * primary key until the owner commits (then gets false) or rolls back (then wins the key itself).
     */
    boolean claim(String key, byte[] requestHash, UUID transferId) {
        return jdbc.sql("""
                        insert into idempotency_keys (key, request_hash, transfer_id)
                        values (:key, :hash, :transferId)
                        on conflict (key) do nothing""")
                .param("key", key)
                .param("hash", requestHash)
                .param("transferId", transferId)
                .update() == 1;
    }

    Optional<StoredKey> find(String key) {
        return jdbc.sql("select request_hash, transfer_id from idempotency_keys where key = :key")
                .param("key", key)
                .query((rs, row) -> new StoredKey(rs.getBytes("request_hash"), rs.getObject("transfer_id", UUID.class)))
                .optional();
    }

    record StoredKey(byte[] requestHash, UUID transferId) {
    }
}
