package dev.tally.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final AccountService service;

    AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<AccountView> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = service.open(request.ownerName(), request.currency());
        return ResponseEntity.created(URI.create("/accounts/" + account.id()))
                .body(AccountView.of(account, List.of()));
    }

    @GetMapping
    List<AccountView> list(@RequestParam(defaultValue = "200") int limit) {
        return service.list(limit).stream().map(a -> AccountView.of(a, List.of())).toList();
    }

    @GetMapping("/{id}")
    AccountView get(@PathVariable UUID id) {
        return AccountView.of(service.get(id), service.recentEntries(id));
    }

    record OpenAccountRequest(
            @NotBlank @Size(max = 200) String ownerName,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code like EUR") String currency) {
    }

    record AccountView(UUID id, String ownerName, String currency, AccountKind kind, long balance,
                       Instant createdAt, List<Entry> entries) {

        static AccountView of(Account a, List<Entry> entries) {
            return new AccountView(a.id(), a.ownerName(), a.currency(), a.kind(), a.balance(), a.createdAt(), entries);
        }
    }
}
