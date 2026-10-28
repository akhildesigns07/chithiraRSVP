package db.migration;

import com.chithira.rsvp.util.Tokens;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Adds SHA-256 hashes of guest and host tokens so lookups go through the hash and the token itself
 * is compared in constant time. Written in Java so existing rows are backfilled with exactly the
 * same hash function the application uses, on PostgreSQL and on the H2 test database alike.
 */
public class V3__Token_hashes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE event ADD COLUMN host_token_hash VARCHAR(64)");
            s.execute("ALTER TABLE guest ADD COLUMN token_hash VARCHAR(64)");
        }
        backfill(c, "event", "host_token", "host_token_hash");
        backfill(c, "guest", "token", "token_hash");
        try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE event ALTER COLUMN host_token_hash SET NOT NULL");
            s.execute("ALTER TABLE guest ALTER COLUMN token_hash SET NOT NULL");
            s.execute("CREATE UNIQUE INDEX uq_event_host_token_hash ON event (host_token_hash)");
            s.execute("CREATE UNIQUE INDEX uq_guest_token_hash ON guest (token_hash)");
        }
    }

    private static void backfill(Connection c, String table, String tokenColumn, String hashColumn) throws Exception {
        try (Statement select = c.createStatement();
             ResultSet rows = select.executeQuery("SELECT id, " + tokenColumn + " FROM " + table);
             PreparedStatement update = c.prepareStatement(
                     "UPDATE " + table + " SET " + hashColumn + " = ? WHERE id = ?")) {
            while (rows.next()) {
                update.setString(1, Tokens.hash(rows.getString(2)));
                update.setLong(2, rows.getLong(1));
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
