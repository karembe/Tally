package dev.tally;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The app's own connection is tally_app: history tables must be append-only for it (P1.2). */
class DatabasePermissionsTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void appRoleCannotRewriteHistory() {
        assertDenied("update ledger_events set payload = payload");
        assertDenied("delete from ledger_events");
        assertDenied("update ledger_entries set amount = amount");
        assertDenied("delete from ledger_entries");
        assertDenied("truncate ledger_events");
        assertDenied("delete from accounts");
    }

    private void assertDenied(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update())
                .as(sql)
                .rootCause()
                .hasMessageContaining("permission denied");
    }
}
