package dev.tally.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private static final String COLUMNS = "id, owner_name, currency, kind, balance, created_at";

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Account account) {
        jdbc.sql("insert into accounts (" + COLUMNS + ") values (:id, :owner, :currency, :kind, :balance, :createdAt)")
                .param("id", account.id())
                .param("owner", account.ownerName())
                .param("currency", account.currency())
                .param("kind", account.kind().name())
                .param("balance", account.balance())
                .param("createdAt", OffsetDateTime.ofInstant(account.createdAt(), ZoneOffset.UTC))
                .update();
    }

    public Optional<Account> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from accounts where id = :id")
                .param("id", id)
                .query(AccountRepository::map)
                .optional();
    }

    public Optional<Account> findTreasury(String currency) {
        return jdbc.sql("select " + COLUMNS + " from accounts where kind = 'TREASURY' and currency = :currency")
                .param("currency", currency)
                .query(AccountRepository::map)
                .optional();
    }

    public List<Account> list(int limit) {
        return jdbc.sql("select " + COLUMNS + " from accounts order by kind desc, created_at desc limit :limit")
                .param("limit", limit)
                .query(AccountRepository::map)
                .list();
    }

    /**
     * Locks the rows in Postgres uuid order, the same order for every transaction, so two opposite
     * transfers (A to B, B to A) can never wait on each other in a cycle.
     */
    public List<Account> lockInIdOrder(Collection<UUID> ids) {
        return jdbc.sql("select " + COLUMNS + " from accounts where id in (:ids) order by id for update")
                .param("ids", ids)
                .query(AccountRepository::map)
                .list();
    }

    public void addToBalance(UUID id, long delta) {
        jdbc.sql("update accounts set balance = balance + :delta where id = :id")
                .param("delta", delta)
                .param("id", id)
                .update();
    }

    public List<Entry> recentEntries(UUID accountId, int limit) {
        return jdbc.sql("""
                        select transfer_id, account_id, amount, direction, created_at
                        from ledger_entries
                        where account_id = :id
                        order by id desc
                        limit :limit""")
                .param("id", accountId)
                .param("limit", limit)
                .query(Entry::map)
                .list();
    }

    private static Account map(ResultSet rs, int row) throws SQLException {
        return new Account(
                rs.getObject("id", UUID.class),
                rs.getString("owner_name"),
                rs.getString("currency"),
                AccountKind.valueOf(rs.getString("kind")),
                rs.getLong("balance"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

}
