package dev.tally;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tally.chain.CanonicalEncoder;
import dev.tally.chain.TransferEvent;
import dev.tally.common.Sha256;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Global invariants that must hold after any sequence of operations, whatever other tests did. */
public final class LedgerAssertions {

    private LedgerAssertions() {
    }

    public static void assertLedgerConsistent(JdbcClient jdbc) {
        assertThat(jdbc.sql("select coalesce(sum(balance), 0) from accounts").query(Long.class).single())
                .as("money is only moved, never created: all balances sum to 0")
                .isZero();
        assertThat(jdbc.sql("select count(*) from accounts where kind = 'USER' and balance < 0").query(Long.class).single())
                .as("no user account is negative")
                .isZero();
        assertThat(jdbc.sql("select count(*) from ledger_entries").query(Long.class).single())
                .as("double entry: two entries per transfer")
                .isEqualTo(2 * jdbc.sql("select count(*) from transfers").query(Long.class).single());
        assertChainValid(jdbc);
    }

    /** Recomputes every hash from genesis, the same check the Go verifier will do in phase 4. */
    public static void assertChainValid(JdbcClient jdbc) {
        List<Row> rows = jdbc.sql("""
                        select e.seq, e.payload, e.prev_hash, e.hash, t.id, t.from_account, t.to_account,
                               t.amount, t.currency, e.created_at
                        from ledger_events e join transfers t on t.id = e.transfer_id
                        order by e.seq""")
                .query((rs, n) -> new Row(rs.getLong(1), rs.getBytes(2), rs.getBytes(3), rs.getBytes(4),
                        new TransferEvent(rs.getLong(1), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                                rs.getObject(7, UUID.class), rs.getLong(8), rs.getString(9),
                                rs.getObject(10, OffsetDateTime.class).toInstant())))
                .list();

        byte[] prev = new byte[32];
        long expectedSeq = 1;
        for (Row row : rows) {
            assertThat(row.seq()).as("no gap in seq").isEqualTo(expectedSeq++);
            assertThat(row.prevHash()).as("prev_hash links to previous event at seq %d", row.seq()).isEqualTo(prev);
            assertThat(row.payload()).as("payload matches transfer row at seq %d", row.seq())
                    .isEqualTo(CanonicalEncoder.encode(row.event()));
            byte[] recomputed = Sha256.hash(prev, row.payload());
            assertThat(Arrays.equals(recomputed, row.hash())).as("hash at seq %d", row.seq()).isTrue();
            prev = row.hash();
        }
        byte[] head = jdbc.sql("select hash from chain_head where id = 1").query((rs, n) -> rs.getBytes(1)).single();
        assertThat(head).as("chain_head points at the last event").isEqualTo(prev);
    }

    private record Row(long seq, byte[] payload, byte[] prevHash, byte[] hash, TransferEvent event) {
    }
}
