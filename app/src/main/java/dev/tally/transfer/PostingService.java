package dev.tally.transfer;

import dev.tally.account.Account;
import dev.tally.account.AccountKind;
import dev.tally.account.AccountRepository;
import dev.tally.chain.EventChain;
import dev.tally.common.ApiException;
import dev.tally.common.UuidV7;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one transfer in ONE transaction: idempotency key, balances, transfer, two entries, chain event.
 * Either all of it commits or none of it does. READ COMMITTED is enough because every row we decide on
 * is locked first (accounts, then chain head, always in that order).
 */
@Service
class PostingService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final IdempotencyRepository keys;
    private final EventChain chain;

    PostingService(AccountRepository accounts, TransferRepository transfers, IdempotencyRepository keys,
                   EventChain chain) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.keys = keys;
        this.chain = chain;
    }

    @Transactional
    Posting post(String idempotencyKey, TransferCommand cmd) {
        byte[] requestHash = cmd.requestHash();
        UUID transferId = UuidV7.next();

        if (!keys.claim(idempotencyKey, requestHash, transferId)) {
            return replay(idempotencyKey, requestHash);
        }
        if (cmd.fromAccount().equals(cmd.toAccount())) {
            throw ApiException.unprocessable("Same account", "cannot transfer from an account to itself");
        }

        Map<UUID, Account> locked = accounts.lockInIdOrder(List.of(cmd.fromAccount(), cmd.toAccount())).stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));
        Account from = require(locked, cmd.fromAccount());
        Account to = require(locked, cmd.toAccount());
        checkRules(cmd, from, to);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        accounts.addToBalance(from.id(), -cmd.amount());
        accounts.addToBalance(to.id(), cmd.amount());
        transfers.insert(transferId, cmd, now);
        transfers.insertEntry(transferId, from.id(), cmd.amount(), "D", now);
        transfers.insertEntry(transferId, to.id(), cmd.amount(), "C", now);
        long seq = chain.append(transferId, from.id(), to.id(), cmd.amount(), cmd.currency(), now);

        Transfer transfer = new Transfer(transferId, from.id(), to.id(), cmd.amount(), cmd.currency(),
                Transfer.COMMITTED, seq, now);
        return new Posting(transfer, false);
    }

    private Posting replay(String key, byte[] requestHash) {
        IdempotencyRepository.StoredKey stored = keys.find(key).orElseThrow();
        if (!MessageDigest.isEqual(stored.requestHash(), requestHash)) {
            throw ApiException.conflict("Idempotency key reused",
                    "key '" + key + "' was already used with a different request");
        }
        return new Posting(transfers.find(stored.transferId()).orElseThrow(), true);
    }

    private static Account require(Map<UUID, Account> locked, UUID id) {
        Account account = locked.get(id);
        if (account == null) {
            throw ApiException.notFound("account " + id + " does not exist");
        }
        return account;
    }

    private static void checkRules(TransferCommand cmd, Account from, Account to) {
        if (!from.currency().equals(cmd.currency()) || !to.currency().equals(cmd.currency())) {
            throw ApiException.unprocessable("Currency mismatch",
                    "transfer is " + cmd.currency() + " but accounts are " + from.currency() + " and " + to.currency());
        }
        if (to.kind() == AccountKind.TREASURY) {
            throw ApiException.unprocessable("Treasury is not a destination", "money cannot be sent back to the treasury");
        }
        if (from.kind() == AccountKind.USER && from.balance() < cmd.amount()) {
            throw ApiException.unprocessable("Insufficient funds",
                    "account " + from.id() + " has " + from.balance() + ", needs " + cmd.amount());
        }
    }

    record Posting(Transfer transfer, boolean replayed) {
    }
}
