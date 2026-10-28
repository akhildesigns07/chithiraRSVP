package db.migration;

import com.chithira.rsvp.util.Slugs;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Open Graph share images (cached per event, keyed by a public slug) and the host's
 * WhatsApp message templates. Java so existing events get slugs from the same generator the app uses.
 */
public class V5__Og_images_and_messages extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE event ADD COLUMN og_slug VARCHAR(80)");
        }
        try (Statement select = c.createStatement();
             ResultSet rows = select.executeQuery("SELECT id, couple_names FROM event");
             PreparedStatement update = c.prepareStatement("UPDATE event SET og_slug = ? WHERE id = ?")) {
            while (rows.next()) {
                update.setString(1, Slugs.forEvent(rows.getString(2)));
                update.setLong(2, rows.getLong(1));
                update.addBatch();
            }
            update.executeBatch();
        }
        try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE event ALTER COLUMN og_slug SET NOT NULL");
            s.execute("CREATE UNIQUE INDEX uq_event_og_slug ON event (og_slug)");
            // version = the event's updated_at (epoch millis) the image was rendered for.
            s.execute("""
                    CREATE TABLE event_og_image (
                        event_id BIGINT PRIMARY KEY REFERENCES event (id) ON DELETE CASCADE,
                        version  BIGINT NOT NULL,
                        jpeg     BYTEA  NOT NULL
                    )""");
            // NULL template = use the default built from the event's own messages.
            s.execute("""
                    CREATE TABLE event_message (
                        event_id    BIGINT PRIMARY KEY REFERENCES event (id) ON DELETE CASCADE,
                        template_en VARCHAR(1000),
                        template_ml VARCHAR(1000),
                        language    VARCHAR(2) NOT NULL
                    )""");
        }
    }
}
