package dev.tally.chain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tally.common.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Locks the byte layout: the Go verifier must reproduce these exact files. */
class CanonicalEncoderTest {

    private static final TransferEvent EVENT_1 = new TransferEvent(
            1L,
            UUID.fromString("01890000-0000-7000-8000-000000000001"),
            UUID.fromString("00000000-0000-7000-8000-000000000978"),
            UUID.fromString("01890000-0000-7000-8000-000000000002"),
            12_345L,
            "EUR",
            Instant.parse("2026-01-01T00:00:00.123456Z"));

    @Test
    void encodingMatchesGoldenVector() throws IOException {
        byte[] payload = CanonicalEncoder.encode(EVENT_1);

        assertThat(HexFormat.of().formatHex(payload)).isEqualTo(vector("event-1.hex"));
    }

    @Test
    void genesisHashMatchesGoldenVector() throws IOException {
        byte[] hash = Sha256.hash(new byte[32], CanonicalEncoder.encode(EVENT_1));

        assertThat(HexFormat.of().formatHex(hash)).isEqualTo(vector("event-1.sha256"));
    }

    @Test
    void anyFieldChangeChangesTheBytes() {
        var otherAmount = new TransferEvent(EVENT_1.seq(), EVENT_1.transferId(), EVENT_1.fromAccount(),
                EVENT_1.toAccount(), EVENT_1.amount() + 1, EVENT_1.currency(), EVENT_1.createdAt());

        assertThat(CanonicalEncoder.encode(otherAmount)).isNotEqualTo(CanonicalEncoder.encode(EVENT_1));
    }

    private static String vector(String name) throws IOException {
        try (InputStream in = CanonicalEncoderTest.class.getResourceAsStream("/vectors/" + name)) {
            assertThat(in).as("vector file %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII).strip();
        }
    }
}
