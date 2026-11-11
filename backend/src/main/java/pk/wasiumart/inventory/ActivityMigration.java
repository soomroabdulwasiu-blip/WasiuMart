package pk.wasiumart.inventory;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Adds nullable movement details without changing existing activity or inventory. */
@Component
public class ActivityMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public ActivityMigration(JdbcTemplate jdbc, TransactionTemplate transactions) { this.jdbc=jdbc; this.transactions=transactions; }
    @Override public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            Set<String> fields=jdbc.query("PRAGMA table_info(inventory_activity)", (rs,i)->rs.getString("name")).stream().collect(Collectors.toSet());
            if (!fields.contains("quantity_delta")) jdbc.execute("ALTER TABLE inventory_activity ADD COLUMN quantity_delta TEXT");
            if (!fields.contains("quantity_unit")) jdbc.execute("ALTER TABLE inventory_activity ADD COLUMN quantity_unit TEXT");
            if (!fields.contains("reason")) jdbc.execute("ALTER TABLE inventory_activity ADD COLUMN reason TEXT");
        });
    }
}
