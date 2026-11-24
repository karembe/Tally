package dev.tally.dev;

import dev.tally.account.Account;
import dev.tally.account.AccountService;
import dev.tally.transfer.TransferCommand;
import dev.tally.transfer.TransferService;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Demo data for local runs (profile "dev" only). Goes through the real services, so seeded money
 * is hash-chained exactly like API traffic. Skipped when user accounts already exist.
 */
@Component
@ConditionalOnProperty(name = "tally.seed.enabled", havingValue = "true")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final int TRANSFERS = 40;

    private final AccountService accounts;
    private final TransferService transfers;
    private final JdbcClient jdbc;

    DevDataSeeder(AccountService accounts, TransferService transfers, JdbcClient jdbc) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        long users = jdbc.sql("select count(*) from accounts where kind = 'USER'").query(Long.class).single();
        if (users > 0) {
            log.info("Seed skipped: {} user accounts already exist", users);
            return;
        }

        List<Account> eur = List.of(
                accounts.open("Alice Martin", "EUR"),
                accounts.open("Bob Dupont", "EUR"),
                accounts.open("Chloe Bernard", "EUR"),
                accounts.open("Driss Alaoui", "EUR"));
        Account usd = accounts.open("Erin Walsh", "USD");

        for (Account a : eur) {
            transfers.fund("seed-fund-" + a.id(), a.id(), 100_000);
        }
        transfers.fund("seed-fund-" + usd.id(), usd.id(), 50_000);

        // Fixed seed: the same demo history on every fresh database.
        Random random = new Random(42);
        for (int i = 0; i < TRANSFERS; i++) {
            Account from = eur.get(random.nextInt(eur.size()));
            Account to = eur.get(random.nextInt(eur.size()));
            if (from.equals(to)) {
                continue;
            }
            long cents = 100 + random.nextInt(5_000);
            transfers.transfer("seed-transfer-" + i + "-" + from.id(),
                    new TransferCommand(from.id(), to.id(), cents, "EUR"));
        }
        log.info("Seeded 5 accounts. Try: curl localhost:8181/accounts/{}", eur.getFirst().id());
    }
}
