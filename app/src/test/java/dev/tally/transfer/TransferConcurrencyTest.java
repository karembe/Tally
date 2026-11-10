package dev.tally.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tally.IntegrationTest;
import dev.tally.LedgerAssertions;
import dev.tally.account.Account;
import dev.tally.account.AccountService;
import dev.tally.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** P1.5 and P1.8: many threads at once must not create money, lose money, or break the chain. */
class TransferConcurrencyTest extends IntegrationTest {

    @Autowired
    AccountService accounts;
    @Autowired
    TransferService transfers;
    @Autowired
    JdbcClient jdbc;

    @Test
    void thousandConcurrentRandomTransfersKeepTheLedgerConsistent() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID id = accounts.open("Load " + i, "EUR").id();
            transfers.fund("fund-" + id, id, 10_000);
            ids.add(id);
        }

        List<Callable<Boolean>> jobs = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            jobs.add(() -> {
                var random = ThreadLocalRandom.current();
                UUID from = ids.get(random.nextInt(ids.size()));
                UUID to = ids.get(random.nextInt(ids.size()));
                try {
                    transfers.transfer(UUID.randomUUID().toString(),
                            new TransferCommand(from, to, 1 + random.nextInt(2_000), "EUR"));
                    return true;
                } catch (ApiException expected) {
                    return false;
                }
            });
        }

        int committed = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            for (Future<Boolean> f : pool.invokeAll(jobs)) {
                committed += f.get() ? 1 : 0;
            }
        }

        assertThat(committed).as("most random transfers succeed").isGreaterThan(500);
        long total = jdbc.sql("select sum(balance) from accounts where id in (:ids)").param("ids", ids)
                .query(Long.class).single();
        assertThat(total).as("money only moved inside the group").isEqualTo(50 * 10_000L);
        LedgerAssertions.assertLedgerConsistent(jdbc);
    }

    @Test
    void twentyParallelRequestsWithOneKeyMakeOneTransfer() throws Exception {
        Account alice = accounts.open("Alice", "EUR");
        Account bob = accounts.open("Bob", "EUR");
        transfers.fund("fund-" + alice.id(), alice.id(), 1_000);
        String key = UUID.randomUUID().toString();
        var cmd = new TransferCommand(alice.id(), bob.id(), 300, "EUR");

        List<Callable<UUID>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> transfers.transfer(key, cmd).transfer().id());
        }
        List<UUID> returned = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (Future<UUID> f : pool.invokeAll(jobs)) {
                returned.add(f.get());
            }
        }

        assertThat(returned).as("every caller sees the same transfer").containsOnly(returned.getFirst());
        assertThat(accounts.get(alice.id()).balance()).isEqualTo(700);
        assertThat(jdbc.sql("select count(*) from transfers where from_account = :a").param("a", alice.id())
                .query(Long.class).single()).isEqualTo(1);
    }
}
