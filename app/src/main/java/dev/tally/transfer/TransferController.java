package dev.tally.transfer;

import dev.tally.account.Entry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransferService service;

    TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<Receipt> create(@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                                   @Valid @RequestBody TransferRequest request) {
        TransferService.Result result = service.transfer(key, request.toCommand());
        return Receipt.response(result);
    }

    @GetMapping
    List<TransferRepository.Summary> recent(@RequestParam(defaultValue = "50") int limit) {
        return service.recent(limit);
    }

    @GetMapping("/{id}")
    Details get(@PathVariable UUID id) {
        Transfer t = service.get(id);
        return new Details(t, service.entries(id));
    }

    record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @Positive Long amount,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code like EUR") String currency) {

        TransferCommand toCommand() {
            return new TransferCommand(fromAccountId, toAccountId, amount, currency);
        }
    }

    /** Body of POST /transfers and /funding; a replay returns the same body with 200 instead of 201. */
    record Receipt(UUID id, String status, long eventSeq, Instant createdAt) {

        static ResponseEntity<Receipt> response(TransferService.Result result) {
            Transfer t = result.transfer();
            Receipt body = new Receipt(t.id(), t.status(), t.eventSeq(), t.createdAt());
            return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                    .location(URI.create("/transfers/" + t.id()))
                    .body(body);
        }
    }

    record Details(Transfer transfer, List<Entry> entries) {
    }
}
