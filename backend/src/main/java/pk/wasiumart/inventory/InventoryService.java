package pk.wasiumart.inventory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Single-process inventory service. SQLite is authoritative; each operation rebuilds the queue. */
@Service
public class InventoryService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryService(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this(jdbc, transactions, Clock.systemDefaultZone());
    }
    InventoryService(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    private GroceryRecord mapRecord(ResultSet rs, int ignored) throws SQLException {
        String expiry = rs.getString("expiry_date");
        return new GroceryRecord(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
            new BigDecimal(rs.getString("quantity")), rs.getString("unit"), new BigDecimal(rs.getString("unit_price")),
            new BigDecimal(rs.getString("low_stock_threshold")), expiry == null ? null : LocalDate.parse(expiry),
            Instant.parse(rs.getString("added_at")));
    }

    private GroceryQueue loadQueue() {
        GroceryQueue queue = new GroceryQueue();
        jdbc.query("SELECT * FROM inventory_records ORDER BY id ASC", this::mapRecord).forEach(queue::enqueue);
        return queue;
    }

    public synchronized List<GroceryRecord> list() { return loadQueue().traverse(); }
    public synchronized Optional<GroceryRecord> find(long id) { return loadQueue().findById(id); }
    public synchronized QueueView queue() {
        GroceryQueue q = loadQueue();
        return new QueueView(q.size(), q.peek().orElse(null), q.peekRear().orElse(null), q.traverse());
    }

    public synchronized GroceryRecord create(InventoryInput input) {
        Objects.requireNonNull(input, "Record is required");
        Instant addedAt = clock.instant();
        GroceryRecord valid = input.asRecord(1, addedAt);
        return transactions.execute(status -> {
            GroceryQueue q = loadQueue();
            jdbc.update("INSERT INTO inventory_records (name,category,quantity,unit,unit_price,low_stock_threshold,expiry_date,added_at) VALUES (?,?,?,?,?,?,?,?)",
                valid.name(), valid.category(), valid.quantity().toPlainString(), valid.unit(), valid.unitPrice().toPlainString(),
                valid.lowStockThreshold().toPlainString(), valid.expiryDate() == null ? null : valid.expiryDate().toString(), addedAt.toString());
            Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            GroceryRecord created = input.asRecord(Objects.requireNonNull(id), addedAt);
            q.enqueue(created);
            log("ENQUEUE", created);
            return created;
        });
    }

    public synchronized GroceryRecord update(long id, InventoryInput input) {
        Objects.requireNonNull(input, "Record is required");
        return transactions.execute(status -> {
            GroceryQueue q = loadQueue();
            GroceryRecord existing = q.findById(id).orElseThrow(() -> new NoSuchElementException("Record not found"));
            GroceryRecord updated = input.asRecord(id, existing.addedAt());
            q.update(updated);
            jdbc.update("UPDATE inventory_records SET name=?,category=?,quantity=?,unit=?,unit_price=?,low_stock_threshold=?,expiry_date=? WHERE id=?",
                updated.name(), updated.category(), updated.quantity().toPlainString(), updated.unit(), updated.unitPrice().toPlainString(),
                updated.lowStockThreshold().toPlainString(), updated.expiryDate() == null ? null : updated.expiryDate().toString(), id);
            log("UPDATE", updated);
            return updated;
        });
    }

    /** Atomically change stock without moving the record in the queue. */
    public synchronized GroceryRecord adjustStock(long id, BigDecimal delta, String reason) {
        if (delta == null || delta.signum() == 0) throw new IllegalArgumentException("Enter a non-zero adjustment");
        String note = reason == null || reason.isBlank() ? "Manual adjustment" : reason.strip();
        if (note.length() > 100) throw new IllegalArgumentException("Reason must be 100 characters or fewer");
        return transactions.execute(status -> {
            GroceryQueue q = loadQueue();
            GroceryRecord old = q.findById(id).orElseThrow(() -> new NoSuchElementException("Record not found"));
            BigDecimal quantity = old.quantity().add(delta);
            if (quantity.signum() < 0) throw new IllegalArgumentException("Adjustment exceeds available stock");
            GroceryRecord updated = new GroceryRecord(old.id(), old.name(), old.category(), quantity, old.unit(),
                old.unitPrice(), old.lowStockThreshold(), old.expiryDate(), old.addedAt());
            q.update(updated);
            jdbc.update("UPDATE inventory_records SET quantity=? WHERE id=?", quantity.toPlainString(), id);
            jdbc.update("INSERT INTO inventory_activity (action,record_id,product_name,occurred_at,quantity_delta,reason,quantity_unit) VALUES (?,?,?,?,?,?,?)",
                delta.signum() > 0 ? "STOCK_IN" : "STOCK_OUT", updated.id(), updated.name(), clock.instant().toString(), delta.toPlainString(), note, updated.unit());
            return updated;
        });
    }

    public synchronized GroceryRecord dequeue(Long expectedId) {
        return transactions.execute(status -> {
            GroceryRecord removed = loadQueue().dequeue().orElseThrow(() -> new QueueEmptyException("Queue is empty"));
            if (expectedId != null && removed.id() != expectedId) throw new QueueEmptyException("Queue changed; refresh and confirm the current front record");
            jdbc.update("DELETE FROM inventory_records WHERE id=?", removed.id());
            log("DEQUEUE", removed);
            return removed;
        });
    }

    public synchronized GroceryRecord delete(long id) {
        return transactions.execute(status -> {
            GroceryRecord removed = loadQueue().deleteById(id).orElseThrow(() -> new NoSuchElementException("Record not found"));
            jdbc.update("DELETE FROM inventory_records WHERE id=?", id);
            log("DELETE", removed);
            return removed;
        });
    }

    private void log(String action, GroceryRecord record) {
        jdbc.update("INSERT INTO inventory_activity (action,record_id,product_name,occurred_at) VALUES (?,?,?,?)",
            action, record.id(), record.name(), clock.instant().toString());
    }

    public synchronized List<Activity> activity() {
        return jdbc.query("SELECT id,action,record_id,product_name,occurred_at,quantity_delta,reason,quantity_unit FROM inventory_activity ORDER BY id DESC LIMIT 100",
            (rs, i) -> new Activity(rs.getLong("id"), rs.getString("action"), rs.getLong("record_id"), rs.getString("product_name"), Instant.parse(rs.getString("occurred_at")), rs.getString("quantity_delta"), rs.getString("reason"), rs.getString("quantity_unit")));
    }

    public synchronized Dashboard dashboard() {
        List<GroceryRecord> records = loadQueue().traverse();
        LocalDate today = LocalDate.now(clock);
        BigDecimal value = records.stream().map(r -> r.quantity().multiply(r.unitPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long low = records.stream().filter(r -> r.quantity().signum() > 0 && r.quantity().compareTo(r.lowStockThreshold()) <= 0).count();
        long out = records.stream().filter(r -> r.quantity().signum() == 0).count();
        long expired = records.stream().filter(r -> r.expiryDate() != null && r.expiryDate().isBefore(today)).count();
        long expiring = records.stream().filter(r -> r.expiryDate() != null && !r.expiryDate().isBefore(today) && !r.expiryDate().isAfter(today.plusDays(7))).count();
        return new Dashboard(records.size(), value, low, out, expired, expiring);
    }

    public record QueueView(int size, GroceryRecord front, GroceryRecord rear, List<GroceryRecord> records) {}
    public record Activity(long id, String action, long recordId, String productName, Instant occurredAt, String quantityDelta, String reason, String quantityUnit) {}
    public record Dashboard(int totalRecords, BigDecimal inventoryValue, long lowStock, long outOfStock, long expired, long expiringWithinSevenDays) {}
    public static class QueueEmptyException extends RuntimeException { public QueueEmptyException(String message) { super(message); } }
}
