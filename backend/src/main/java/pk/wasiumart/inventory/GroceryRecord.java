package pk.wasiumart.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Objects;

/** Immutable details for one inventory entry. The ID and added time identify an entry. */
public record GroceryRecord(long id, String name, String category, BigDecimal quantity,
                            String unit, BigDecimal unitPrice, BigDecimal lowStockThreshold,
                            LocalDate expiryDate, Instant addedAt) {
    public GroceryRecord {
        if (id <= 0) throw new IllegalArgumentException("ID must be positive");
        name = required(name, "Name");
        category = required(category, "Category");
        unit = required(unit, "Unit");
        quantity = nonNegative(quantity, "Quantity");
        unitPrice = nonNegative(unitPrice, "Unit price");
        lowStockThreshold = nonNegative(lowStockThreshold, "Low-stock threshold");
        Objects.requireNonNull(addedAt, "Added time is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(field + " must be zero or greater");
        return value;
    }
}
