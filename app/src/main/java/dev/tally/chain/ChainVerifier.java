package dev.tally.chain;

import dev.tally.common.Sha256;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Recomputes the chain from genesis inside the ledger itself. Useful for the console, but not a proof:
 * a DBA can fool this check. The independent Go verifier against anchored heads arrives in phase 4.
 */
@Component
public class ChainVerifier {

    private final JdbcClient jdbc;

    ChainVerifier(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Result verify() {
        var state = new State();
        jdbc.sql("""
                        select e.seq, e.payload, e.prev_hash, e.hash, t.id, t.from_account, t.to_account,
                               t.amount, t.currency, e.created_at
                        from ledger_events e join transfers t on t.id = e.transfer_id
                        order by e.seq""")
                .query(rs -> {
                    if (state.problem.get() != null) {
                        return;
                    }
                    long seq = rs.getLong(1);
                    byte[] payload = rs.getBytes(2);
                    var event = new TransferEvent(seq, rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                            rs.getObject(7, UUID.class), rs.getLong(8), rs.getString(9),
                            rs.getObject(10, OffsetDateTime.class).toInstant());
                    state.check(seq, payload, rs.getBytes(3), rs.getBytes(4), event);
                });

        Head head = jdbc.sql("select seq, hash from chain_head where id = 1")
                .query((rs, n) -> new Head(rs.getLong(1), rs.getBytes(2)))
                .single();
        if (state.problem.get() == null && (head.seq() != state.lastSeq || !Arrays.equals(head.hash(), state.prev))) {
            state.fail(head.seq(), "chain head does not point at the last event");
        }
        Problem p = state.problem.get();
        return new Result(p == null, state.lastSeq, head.seq(), head.hash(),
                p == null ? null : p.seq(), p == null ? null : p.reason());
    }

    public record Result(boolean valid, long eventsChecked, long headSeq, byte[] headHash, Long brokenAtSeq,
                         String problem) {
    }

    private record Head(long seq, byte[] hash) {
    }

    private record Problem(long seq, String reason) {
    }

    private static final class State {
        byte[] prev = new byte[32];
        long lastSeq;
        final AtomicReference<Problem> problem = new AtomicReference<>();

        void check(long seq, byte[] payload, byte[] prevHash, byte[] hash, TransferEvent event) {
            if (seq != lastSeq + 1) {
                fail(seq, "gap in sequence after " + lastSeq);
            } else if (!Arrays.equals(prevHash, prev)) {
                fail(seq, "prev_hash does not match the previous event");
            } else if (!Arrays.equals(payload, CanonicalEncoder.encode(event))) {
                fail(seq, "payload does not match the transfer row");
            } else if (!Arrays.equals(hash, Sha256.hash(prev, payload))) {
                fail(seq, "stored hash does not match the recomputed hash");
            }
            prev = hash;
            lastSeq = seq;
        }

        void fail(long seq, String reason) {
            problem.compareAndSet(null, new Problem(seq, reason));
        }
    }
}
