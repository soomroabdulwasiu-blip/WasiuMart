# Phase 3: Java queue design

The queue is a custom singly linked list of grocery records, with references to both front and rear. It is an in-memory DSA component, independent of Spring and SQLite. The inventory service reconstructs it from SQLite records in ascending insertion ID order for each operation.

- `enqueue`: validates uniqueness by scanning IDs, then links a node at the rear.
- `dequeue`: removes the front record; returns an empty `Optional` for an empty queue.
- `peek` / `peekRear`: inspect the endpoints without removal.
- `traverse`: returns an immutable snapshot ordered front to rear.
- `findById` and `update`: scan for a record. Update preserves ID, added time, and position.
- `deleteById`: removes a chosen node while preserving remaining order. This is an extension for CRUD, not FIFO dequeue.

Records are immutable; quantity, prices and thresholds use `BigDecimal`. IDs are positive and unique within the queue. Methods are synchronized to protect in-memory invariants, and the service serializes operations within one application process. SQLite mutations and their activity entries use one transaction. Multiple application processes writing to the same database are not supported.

## Complexity

For n records: dequeue, peek, peekRear, size and isEmpty take O(1). Enqueue is O(n) overall because duplicate IDs are checked by traversal; linking itself is O(1). Finding, updating, selected deletion and traversal take O(n). A traversal snapshot uses O(n) extra space; the other operations use O(1) extra space. The queue itself uses O(n) space.

FIFO is based on insertion order, not expiry date. Removing a selected record does not change the relative order of other records.

Rebuilding the queue currently takes O(n²) time because each enqueue scans for duplicate IDs. This favors an explicit, understandable implementation for small classroom datasets; optimization is future work.
