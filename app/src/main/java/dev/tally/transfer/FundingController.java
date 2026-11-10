package dev.tally.transfer;

import dev.tally.web.AdminTokenGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/funding")
class FundingController {

    static final String ADMIN_TOKEN = "X-Admin-Token";

    private final TransferService service;
    private final AdminTokenGuard admin;

    FundingController(TransferService service, AdminTokenGuard admin) {
        this.service = service;
        this.admin = admin;
    }

    @PostMapping
    ResponseEntity<TransferController.Receipt> fund(
            @RequestHeader(name = ADMIN_TOKEN, required = false) String token,
            @RequestHeader(name = TransferController.IDEMPOTENCY_KEY, required = false) String key,
            @Valid @RequestBody FundingRequest request) {
        admin.check(token);
        return TransferController.Receipt.response(service.fund(key, request.toAccountId(), request.amount()));
    }

    record FundingRequest(@NotNull UUID toAccountId, @NotNull @Positive Long amount) {
    }
}
