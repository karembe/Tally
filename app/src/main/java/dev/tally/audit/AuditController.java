package dev.tally.audit;

import dev.tally.chain.ChainVerifier;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only views for the console: the event chain and the ledger's own integrity checks. */
@RestController
class AuditController {

    private static final HexFormat HEX = HexFormat.of();

    private final JdbcClient jdbc;
    private final ChainVerifier verifier;

    AuditController(JdbcClient jdbc, ChainVerifier verifier) {
        this.jdbc = jdbc;
        this.verifier = verifier;
    }

    @GetMapping("/events")
    List<EventView> events(@RequestParam(required = false) Long before,
                           @RequestParam(defaultValue = "50") int limit) {
        return jdbc.sql("""
                        select e.seq, e.transfer_id, e.prev_hash, e.hash, e.created_at,
                               t.amount, t.currency, fa.owner_name as from_owner, ta.owner_name as to_owner
                        from ledger_events e
                        join transfers t on t.id = e.transfer_id
                        join accounts fa on fa.id = t.from_account
                        join accounts ta on ta.id = t.to_account
                        where e.seq < :before
                        order by e.seq desc
                        limit :limit""")
                .param("before", before == null ? Long.MAX_VALUE : before)
                .param("limit", Math.clamp(limit, 1, 200))
                .query((rs, n) -> new EventView(
                        rs.getLong("seq"),
                        rs.getObject("transfer_id", UUID.class),
                        HEX.formatHex(rs.getBytes("prev_hash")),
                        HEX.formatHex(rs.getBytes("hash")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("amount"),
                        rs.getString("currency"),
                        rs.getString("from_owner"),
                        rs.getString("to_owner")))
                .list();
    }

    @GetMapping("/integrity")
    Integrity integrity() {
        long sum = jdbc.sql("select coalesce(sum(balance), 0) from accounts").query(Long.class).single();
        long transfers = jdbc.sql("select count(*) from transfers").query(Long.class).single();
        long entries = jdbc.sql("select count(*) from ledger_entries").query(Long.class).single();
        long negativeUsers = jdbc.sql("select count(*) from accounts where kind = 'USER' and balance < 0")
                .query(Long.class).single();
        ChainVerifier.Result chain = verifier.verify();
        return new Integrity(Instant.now(), sum, transfers, entries, negativeUsers, chain.valid(),
                chain.eventsChecked(), chain.headSeq(), HEX.formatHex(chain.headHash()), chain.brokenAtSeq(),
                chain.problem());
    }

    record EventView(long seq, UUID transferId, String prevHash, String hash, Instant createdAt, long amount,
                     String currency, String fromOwner, String toOwner) {
    }

    record Integrity(Instant checkedAt, long sumOfBalances, long transfers, long entries,
                     long negativeUserAccounts, boolean chainValid, long eventsChecked, long headSeq,
                     String headHash, Long brokenAtSeq, String problem) {
    }
}
