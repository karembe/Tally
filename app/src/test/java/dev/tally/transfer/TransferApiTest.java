package dev.tally.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tally.IntegrationTest;
import dev.tally.LedgerAssertions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/** End-to-end over HTTP: the API contract of phase 1. */
class TransferApiTest extends IntegrationTest {

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
    }

    @Test
    void fundTransferAndReadBack() {
        String alice = openAccount("Alice", "EUR");
        String bob = openAccount("Bob", "EUR");

        assertThat(fund(alice, 10_000, IntegrationTest.ADMIN_TOKEN).status()).isEqualTo(201);
        Reply sent = transfer(key(), alice, bob, 2_500, "EUR");

        assertThat(sent.status()).isEqualTo(201);
        assertThat(sent.body()).containsEntry("status", "COMMITTED").containsKey("eventSeq");
        assertThat(balance(alice)).isEqualTo(7_500);
        assertThat(balance(bob)).isEqualTo(2_500);

        Reply details = get("/transfers/" + sent.body().get("id"));
        assertThat((List<?>) details.body().get("entries")).hasSize(2);
        LedgerAssertions.assertLedgerConsistent(jdbc);
    }

    @Test
    void replayReturnsSameReceiptAndMovesMoneyOnce() {
        String alice = funded("EUR", 5_000);
        String bob = openAccount("Bob", "EUR");
        String key = key();

        Reply first = transfer(key, alice, bob, 1_000, "EUR");
        Reply replay = transfer(key, alice, bob, 1_000, "EUR");
        Reply reused = transfer(key, alice, bob, 9, "EUR");

        assertThat(first.status()).isEqualTo(201);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(reused.status()).isEqualTo(409);
        assertThat(balance(alice)).isEqualTo(4_000);
    }

    @Test
    void businessErrorsAreProblemJson() {
        String alice = funded("EUR", 100);
        String bob = openAccount("Bob", "EUR");
        String dollars = openAccount("Erin", "USD");

        assertThat(transfer(key(), alice, bob, 101, "EUR").status()).isEqualTo(422);
        assertThat(transfer(key(), alice, dollars, 10, "EUR").status()).isEqualTo(422);
        assertThat(transfer(key(), alice, UUID.randomUUID().toString(), 10, "EUR").status()).isEqualTo(404);
        assertThat(transfer(key(), alice, bob, -5, "EUR").status()).isEqualTo(400);
        assertThat(transfer(null, alice, bob, 5, "EUR").status()).isEqualTo(400);
        assertThat(fund(alice, 10, "wrong-token").status()).isEqualTo(401);

        Reply problem = transfer(key(), alice, bob, 101, "EUR");
        assertThat(problem.contentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(problem.body()).containsEntry("title", "Insufficient funds");
    }

    private String funded(String currency, long amount) {
        String id = openAccount("Funded", currency);
        fund(id, amount, IntegrationTest.ADMIN_TOKEN);
        return id;
    }

    private String openAccount(String owner, String currency) {
        return (String) post("/accounts", null, null, Map.of("ownerName", owner, "currency", currency)).body().get("id");
    }

    private Reply fund(String account, long amount, String token) {
        return post("/funding", key(), token, Map.of("toAccountId", account, "amount", amount));
    }

    private Reply transfer(String key, String from, String to, long amount, String currency) {
        return post("/transfers", key, null,
                Map.of("fromAccountId", from, "toAccountId", to, "amount", amount, "currency", currency));
    }

    private long balance(String account) {
        return ((Number) get("/accounts/" + account).body().get("balance")).longValue();
    }

    private Reply post(String path, String key, String token, Map<String, Object> body) {
        return http.post().uri(path)
                .headers(h -> {
                    if (key != null) h.set("Idempotency-Key", key);
                    if (token != null) h.set("X-Admin-Token", token);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> Reply.of(res.getStatusCode().value(), res.getHeaders().getFirst("Content-Type"),
                        res.bodyTo(Map.class)));
    }

    private Reply get(String path) {
        return http.get().uri(path)
                .exchange((req, res) -> Reply.of(res.getStatusCode().value(), res.getHeaders().getFirst("Content-Type"),
                        res.bodyTo(Map.class)));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    record Reply(int status, String contentType, Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        static Reply of(int status, String contentType, Map<?, ?> body) {
            return new Reply(status, contentType, (Map<String, Object>) body);
        }
    }
}
