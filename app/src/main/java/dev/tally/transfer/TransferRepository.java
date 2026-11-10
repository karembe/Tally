package dev.tally.transfer;

import dev.tally.account.Entry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TransferRepository {

    private final JdbcClient jdbc;

    TransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, TransferCommand cmd, Instant createdAt) {
        jdbc.sql("""
                        insert into transfers (id, from_account, to_account, amount, currency, status, created_at)
                        values (:id, :from, :to, :amount, :currency, :status, :createdAt)""")
                .param("id", id)
                .param("from", cmd.fromAccount())
                .param("to", cmd.toAccount())
                .param("amount", cmd.amount())
                .param("currency", cmd.currency())
                .param("status", Transfer.COMMITTED)
                .param("createdAt", utc(createdAt))
                .update();
    }

    void insertEntry(UUID transferId, UUID accountId, long amount, String direction, Instant createdAt) {
        jdbc.sql("""
                        insert into ledger_entries (transfer_id, account_id, amount, direction, created_at)
                        values (:transferId, :accountId, :amount, :direction, :createdAt)""")
                .param("transferId", transferId)
                .param("accountId", accountId)
                .param("amount", amount)
                .param("direction", direction)
                .param("createdAt", utc(createdAt))
                .update();
    }

    Optional<Transfer> find(UUID id) {
        return jdbc.sql("""
                        select t.id, t.from_account, t.to_account, t.amount, t.currency, t.status, t.created_at, e.seq
                        from transfers t
                        join ledger_events e on e.transfer_id = t.id
                        where t.id = :id""")
                .param("id", id)
                .query((rs, row) -> new Transfer(
                        rs.getObject("id", UUID.class),
                        rs.getObject("from_account", UUID.class),
                        rs.getObject("to_account", UUID.class),
                        rs.getLong("amount"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getLong("seq"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    List<Summary> recent(int limit) {
        return jdbc.sql("""
                        select t.id, t.amount, t.currency, t.status, t.created_at, e.seq,
                               t.from_account, fa.owner_name as from_owner, t.to_account, ta.owner_name as to_owner
                        from transfers t
                        join ledger_events e on e.transfer_id = t.id
                        join accounts fa on fa.id = t.from_account
                        join accounts ta on ta.id = t.to_account
                        order by e.seq desc
                        limit :limit""")
                .param("limit", limit)
                .query((rs, row) -> new Summary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("from_account", UUID.class),
                        rs.getString("from_owner"),
                        rs.getObject("to_account", UUID.class),
                        rs.getString("to_owner"),
                        rs.getLong("amount"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getLong("seq"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    List<Entry> entries(UUID transferId) {
        return jdbc.sql("""
                        select transfer_id, account_id, amount, direction, created_at
                        from ledger_entries where transfer_id = :id order by direction desc""")
                .param("id", transferId)
                .query(Entry::map)
                .list();
    }

    private static OffsetDateTime utc(Instant t) {
        return OffsetDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    record Summary(UUID id, UUID fromAccount, String fromOwner, UUID toAccount, String toOwner, long amount,
                   String currency, String status, long eventSeq, Instant createdAt) {
    }
}
