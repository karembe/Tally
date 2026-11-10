package dev.tally.chain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Turns an event into exactly one byte sequence, so the Go verifier can rebuild the same hash.
 * Layout: version byte, then each field as [uint32 length][bytes], all integers big-endian:
 * seq int64 | transfer_id 16 | from 16 | to 16 | amount int64 | currency 3 ASCII | created_at int64 micros UTC.
 * Any change here breaks every existing hash: bump VERSION and update the golden vectors.
 */
public final class CanonicalEncoder {

    public static final byte VERSION = 1;

    private CanonicalEncoder() {
    }

    public static byte[] encode(TransferEvent e) {
        var bytes = new ByteArrayOutputStream(128);
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            field(out, int64(e.seq()));
            field(out, uuid(e.transferId()));
            field(out, uuid(e.fromAccount()));
            field(out, uuid(e.toAccount()));
            field(out, int64(e.amount()));
            field(out, e.currency().getBytes(StandardCharsets.US_ASCII));
            field(out, int64(epochMicros(e.createdAt())));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }

    public static long epochMicros(Instant t) {
        return Math.addExact(Math.multiplyExact(t.getEpochSecond(), 1_000_000L), t.getNano() / 1_000);
    }

    private static void field(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] int64(long v) {
        return ByteBuffer.allocate(Long.BYTES).putLong(v).array();
    }

    private static byte[] uuid(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
