package dev.tally.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tally.IntegrationTest;
import dev.tally.LedgerAssertions;
import dev.tally.account.Account;
import dev.tally.account.AccountService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** P1.9: a connection killed mid-transfer leaves nothing behind, and the client's retry succeeds once. */
class TransferCrashTest extends IntegrationTest {

    @Autowired
    AccountService accounts;
    @Autowired
    TransferService transfers;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void killedTransactionLeavesNoPartialRowsAndRetryWorksOnce() throws Exception {
        Account alice = accounts.open("Alice", "EUR");
        Account bob = accounts.open("Bob", "EUR");
        transfers.fund("fund-" + alice.id(), alice.id(), 1_000);
        String key = UUID.randomUUID().toString();
        var cmd = new TransferCommand(alice.id(), bob.id(), 400, "EUR");

        try (Connection owner = superuser(); Connection monitor = superuser()) {
            owner.setAutoCommit(false);
            // Holding the chain head freezes the transfer after balances and entries are written, before commit.
            owner.createStatement().execute("select * from chain_head for update");

            var inFlight = CompletableFuture.supplyAsync(() -> transfers.transfer(key, cmd));
            int pid = waitForBlockedBackend(monitor);
            owner.createStatement().execute("select pg_terminate_backend(" + pid + ")");
            owner.rollback();

            assertThatThrownBy(inFlight::get).isInstanceOf(ExecutionException.class);
        }

        assertThat(accounts.get(alice.id()).balance()).isEqualTo(1_000);
        assertThat(accounts.get(bob.id()).balance()).isZero();
        assertThat(jdbc.sql("select count(*) from idempotency_keys where key = :k").param("k", key)
                .query(Long.class).single()).as("the key died with the transaction").isZero();

        transfers.transfer(key, cmd);
        transfers.transfer(key, cmd);
        assertThat(accounts.get(bob.id()).balance()).isEqualTo(400);
        LedgerAssertions.assertLedgerConsistent(jdbc);
    }

    private Connection superuser() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    /** Polls from its own autocommit connection: pg_stat_activity is frozen inside an open transaction. */
    private static int waitForBlockedBackend(Connection monitor) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ResultSet rs = monitor.createStatement().executeQuery("""
                    select pid from pg_stat_activity
                    where wait_event_type = 'Lock' and query like '%chain_head%for update%'""");
            if (rs.next()) {
                return rs.getInt(1);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("transfer never reached the chain head lock");
    }
}
