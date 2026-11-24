package dev.tally.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tally.IntegrationTest;
import dev.tally.LedgerAssertions;
import dev.tally.account.AccountService;
import dev.tally.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** P1.4: money is only moved, never created or lost, whatever the order of transfers. */
class BalanceInvariantTest extends IntegrationTest {

    @Autowired
    AccountService accounts;
    @Autowired
    TransferService transfers;
    @Autowired
    JdbcClient jdbc;

    @Test
    void sumOfBalancesStaysZeroAfterHundredRandomTransfers() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = accounts.open("Invariant " + i, "EUR").id();
            transfers.fund(UUID.randomUUID().toString(), id, 5_000);
            ids.add(id);
        }

        Random random = new Random(7);
        int committed = 0;
        for (int i = 0; i < 100; i++) {
            UUID from = ids.get(random.nextInt(ids.size()));
            UUID to = ids.get(random.nextInt(ids.size()));
            try {
                transfers.transfer(UUID.randomUUID().toString(),
                        new TransferCommand(from, to, 1 + random.nextInt(3_000), "EUR"));
                committed++;
            } catch (ApiException rejected) {
                // Same account or insufficient funds: the ledger must be unchanged, checked below.
            }
        }

        assertThat(committed).isPositive();
        long groupTotal = jdbc.sql("select sum(balance) from accounts where id in (:ids)").param("ids", ids)
                .query(Long.class).single();
        assertThat(groupTotal).isEqualTo(5 * 5_000L);
        LedgerAssertions.assertLedgerConsistent(jdbc);
    }
}
