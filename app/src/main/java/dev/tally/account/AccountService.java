package dev.tally.account;

import dev.tally.common.ApiException;
import dev.tally.common.UuidV7;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    static final int RECENT_ENTRIES = 50;

    private final AccountRepository accounts;

    AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account open(String ownerName, String currency) {
        Account account = new Account(UuidV7.next(), ownerName.strip(), currency, AccountKind.USER, 0,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        accounts.insert(account);
        return account;
    }

    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> ApiException.notFound("account " + id + " does not exist"));
    }

    public List<Account> list(int limit) {
        return accounts.list(Math.clamp(limit, 1, 500));
    }

    public List<Entry> recentEntries(UUID id) {
        return accounts.recentEntries(id, RECENT_ENTRIES);
    }
}
