package pk.wasiumart.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Client-supplied fields; ID and arrival time are controlled by the server. */
public record InventoryInput(String name, String category, BigDecimal quantity, String unit,
                             BigDecimal unitPrice, BigDecimal lowStockThreshold, LocalDate expiryDate) {
    GroceryRecord asRecord(long id, Instant addedAt) {
        return new GroceryRecord(id, name, category, quantity, unit, unitPrice, lowStockThreshold, expiryDate, addedAt);
    }
}
