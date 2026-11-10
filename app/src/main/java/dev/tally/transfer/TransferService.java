package dev.tally.transfer;

import dev.tally.account.Account;
import dev.tally.account.AccountKind;
import dev.tally.account.AccountRepository;
import dev.tally.account.Entry;
import dev.tally.common.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Entry point for money movement: validates the key, records metrics, delegates the write to PostingService. */
@Service
public class TransferService {

    static final int MAX_KEY_LENGTH = 64;

    private final PostingService posting;
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final MeterRegistry metrics;
    private final Timer duration;

    TransferService(PostingService posting, AccountRepository accounts, TransferRepository transfers,
                    MeterRegistry metrics) {
        this.posting = posting;
        this.accounts = accounts;
        this.transfers = transfers;
        this.metrics = metrics;
        this.duration = Timer.builder("tally.transfer.duration")
                .description("Time to post one transfer, commit included")
                .publishPercentileHistogram()
                .register(metrics);
    }

    public Result transfer(String idempotencyKey, TransferCommand cmd) {
        requireValidKey(idempotencyKey);
        Timer.Sample sample = Timer.start(metrics);
        try {
            PostingService.Posting p = posting.post(idempotencyKey, cmd);
            count(p.replayed() ? "replayed" : "committed");
            return new Result(p.transfer(), p.replayed());
        } catch (ApiException e) {
            count("rejected");
            throw e;
        } catch (RuntimeException e) {
            count("failed");
            throw e;
        } finally {
            sample.stop(duration);
        }
    }

    /** Funding is a normal transfer whose source is the treasury of the account's currency. */
    public Result fund(String idempotencyKey, UUID toAccount, long amount) {
        Account to = accounts.findById(toAccount)
                .orElseThrow(() -> ApiException.notFound("account " + toAccount + " does not exist"));
        if (to.kind() != AccountKind.USER) {
            throw ApiException.unprocessable("Not a user account", "only user accounts can be funded");
        }
        Account treasury = accounts.findTreasury(to.currency())
                .orElseThrow(() -> ApiException.unprocessable("No treasury", "no treasury for " + to.currency()));
        return transfer(idempotencyKey, new TransferCommand(treasury.id(), to.id(), amount, to.currency()));
    }

    public Transfer get(UUID id) {
        return transfers.find(id).orElseThrow(() -> ApiException.notFound("transfer " + id + " does not exist"));
    }

    public List<Entry> entries(UUID transferId) {
        return transfers.entries(transferId);
    }

    private void count(String status) {
        Counter.builder("tally.transfers").tag("status", status).register(metrics).increment();
    }

    private static void requireValidKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw ApiException.badRequest("Idempotency-Key header is required, 1 to " + MAX_KEY_LENGTH + " characters");
        }
    }

    public record Result(Transfer transfer, boolean replayed) {
    }
}
