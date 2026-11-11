package pk.wasiumart.inventory;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GroceryQueueTest {
    private GroceryRecord item(long id, String name) {
        return new GroceryRecord(id, name, "Groceries", BigDecimal.TEN, "pieces", new BigDecimal("12.50"), BigDecimal.ONE, null, Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test void emptyQueueOperationsAreSafe() {
        GroceryQueue queue = new GroceryQueue();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertTrue(queue.peek().isEmpty());
        assertTrue(queue.peekRear().isEmpty());
        assertTrue(queue.dequeue().isEmpty());
        assertTrue(queue.deleteById(99).isEmpty());
        assertFalse(queue.update(item(99, "Missing")));
    }

    @Test void enqueueAndDequeueFollowFifoAndCanBeReused() {
        GroceryQueue queue = new GroceryQueue();
        var rice = item(1, "Rice"); var milk = item(2, "Milk"); var tea = item(3, "Tea");
        queue.enqueue(rice); queue.enqueue(milk); queue.enqueue(tea);
        assertEquals(rice, queue.peek().orElseThrow());
        assertEquals(tea, queue.peekRear().orElseThrow());
        assertEquals(List.of(rice, milk, tea), queue.traverse());
        assertEquals(rice, queue.dequeue().orElseThrow());
        assertEquals(milk, queue.dequeue().orElseThrow());
        assertEquals(tea, queue.dequeue().orElseThrow());
        assertTrue(queue.isEmpty());
        assertTrue(queue.peekRear().isEmpty());
        queue.enqueue(milk);
        assertEquals(milk, queue.peek().orElseThrow());
        assertEquals(milk, queue.peekRear().orElseThrow());
    }

    @Test void updatePreservesPositionAndIdentity() {
        GroceryQueue queue = new GroceryQueue();
        queue.enqueue(item(1, "Rice")); queue.enqueue(item(2, "Milk")); queue.enqueue(item(3, "Tea"));
        assertTrue(queue.update(item(2, "Fresh Milk")));
        assertEquals(List.of("Rice", "Fresh Milk", "Tea"), queue.traverse().stream().map(GroceryRecord::name).toList());
        assertEquals("Fresh Milk", queue.findById(2).orElseThrow().name());
        var changedTime = new GroceryRecord(2, "Milk", "Dairy", BigDecimal.ONE, "L", BigDecimal.ONE, BigDecimal.ZERO, null, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> queue.update(changedTime));
        assertEquals("Fresh Milk", queue.findById(2).orElseThrow().name());
    }

    @Test void selectedDeletionHandlesHeadMiddleTailAndOnlyNode() {
        GroceryQueue queue = new GroceryQueue();
        for (int i = 1; i <= 4; i++) queue.enqueue(item(i, "Item " + i));
        assertEquals(2, queue.deleteById(2).orElseThrow().id());
        assertEquals(1, queue.deleteById(1).orElseThrow().id());
        assertEquals(4, queue.deleteById(4).orElseThrow().id());
        assertEquals(3, queue.peek().orElseThrow().id());
        assertEquals(3, queue.peekRear().orElseThrow().id());
        assertEquals(1, queue.size());
        assertEquals(3, queue.deleteById(3).orElseThrow().id());
        assertTrue(queue.isEmpty());
        queue.enqueue(item(5, "New"));
        assertEquals(5, queue.dequeue().orElseThrow().id());
    }

    @Test void duplicateIdsAreRejectedAndSnapshotsCannotMutateQueue() {
        GroceryQueue queue = new GroceryQueue();
        queue.enqueue(item(1, "Rice"));
        var snapshot = queue.traverse();
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(item(1, "Duplicate")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        queue.enqueue(item(2, "Milk"));
        assertEquals(1, snapshot.size());
        assertEquals(2, queue.size());
    }

    @Test void invalidRecordsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(0, "Rice"));
        assertThrows(IllegalArgumentException.class, () -> item(1, "  "));
        assertThrows(IllegalArgumentException.class, () -> new GroceryRecord(1, "Rice", "Grains", new BigDecimal("-1"), "kg", BigDecimal.ONE, BigDecimal.ZERO, null, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new GroceryRecord(1, "Rice", "Grains", BigDecimal.ONE, "kg", new BigDecimal("-1"), BigDecimal.ZERO, null, Instant.now()));
    }
}
