package pk.wasiumart.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A linked FIFO queue. InventoryService rebuilds this in-memory structure from SQLite. */
public final class GroceryQueue {
    private static final class Node {
        private GroceryRecord value;
        private Node next;
        private Node(GroceryRecord value) { this.value = value; }
    }

    private Node front;
    private Node rear;
    private int size;

    /** Adds at the rear. Duplicate IDs are rejected; the uniqueness scan is O(n). */
    public synchronized void enqueue(GroceryRecord record) {
        Objects.requireNonNull(record, "Record is required");
        if (findNode(record.id()) != null) throw new IllegalArgumentException("Record ID already exists: " + record.id());
        Node node = new Node(record);
        if (rear == null) front = node;
        else rear.next = node;
        rear = node;
        size++;
    }

    /** Removes only the front record, or returns empty when the queue is empty. */
    public synchronized Optional<GroceryRecord> dequeue() {
        if (front == null) return Optional.empty();
        GroceryRecord removed = front.value;
        front = front.next;
        size--;
        if (front == null) rear = null;
        return Optional.of(removed);
    }

    public synchronized Optional<GroceryRecord> peek() {
        return front == null ? Optional.empty() : Optional.of(front.value);
    }

    public synchronized Optional<GroceryRecord> peekRear() {
        return rear == null ? Optional.empty() : Optional.of(rear.value);
    }

    public synchronized int size() { return size; }
    public synchronized boolean isEmpty() { return size == 0; }

    /** Returns an immutable snapshot in front-to-rear order. */
    public synchronized List<GroceryRecord> traverse() {
        List<GroceryRecord> records = new ArrayList<>(size);
        for (Node current = front; current != null; current = current.next) records.add(current.value);
        return List.copyOf(records);
    }

    public synchronized Optional<GroceryRecord> findById(long id) {
        Node node = findNode(id);
        return node == null ? Optional.empty() : Optional.of(node.value);
    }

    /** Replaces details in place while preserving identity, arrival time and queue position. */
    public synchronized boolean update(GroceryRecord replacement) {
        Objects.requireNonNull(replacement, "Replacement is required");
        Node node = findNode(replacement.id());
        if (node == null) return false;
        if (!node.value.addedAt().equals(replacement.addedAt())) {
            throw new IllegalArgumentException("Added time cannot be changed");
        }
        node.value = replacement;
        return true;
    }

    /** Removes a selected entry. This is not a FIFO dequeue. */
    public synchronized Optional<GroceryRecord> deleteById(long id) {
        Node previous = null;
        Node current = front;
        while (current != null && current.value.id() != id) {
            previous = current;
            current = current.next;
        }
        if (current == null) return Optional.empty();
        if (previous == null) front = current.next;
        else previous.next = current.next;
        if (current == rear) rear = previous;
        size--;
        return Optional.of(current.value);
    }

    private Node findNode(long id) {
        for (Node current = front; current != null; current = current.next) {
            if (current.value.id() == id) return current;
        }
        return null;
    }
}
