package dev.tally.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One side of a transfer: D = debit (money out), C = credit (money in). */
public record Entry(UUID transferId, UUID accountId, long amount, String direction, Instant createdAt) {

    public static Entry map(ResultSet rs, int row) throws SQLException {
        return new Entry(
                rs.getObject("transfer_id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getLong("amount"),
                rs.getString("direction"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
