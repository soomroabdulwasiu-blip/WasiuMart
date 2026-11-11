# WasiuMart report and presentation preparation

This is a preparation guide, not the final Word report or a recorded video. Replace placeholders with your own details and verified screenshots, then upload the project and presentation as the assignment requires.

## Suggested Word report

1. Cover: WasiuMart Grocery Inventory Management System; SW 212; Group C; names and IDs; instructor; date.
2. Problem and objectives: describe grocery stock organization, expiry awareness, and FIFO record management.
3. Design: React frontend, Spring Boot backend, SQLite database and custom linked-node Java queue. Explain that each inventory record is one arrival batch and its ID is its batch number.
4. Queue implementation: include `backend/src/main/java/pk/wasiumart/inventory/GroceryQueue.java` and explain its methods and `GroceryRecord` data.
5. CRUD output: include your own screenshots for adding records, viewing/searching, updating a record, dequeueing the front and deleting a selected record. Explain that selected deletion is a separate non-FIFO operation.
6. Results and testing: explain the demonstrated FIFO sequence and verified validation, persistence and movement-history behavior. Include actual test output; do not invent measurements.
7. Complexity: enqueue is O(n) overall because it scans for duplicate IDs; linked-node insertion is O(1). Dequeue and peek are O(1) in the in-memory queue; traversal, update and selected deletion are O(n). The backend currently reconstructs the queue from SQLite in O(n²) for each service operation.
8. Limitations and recommendations: local single-process application, queue order by insertion rather than expiry, activity interface limited to the latest 100 entries, and no complete sales accounting.
9. Links: [insert GitHub project link] and [insert Google Drive video link].

## Presentation walkthrough

1. Explain the grocery problem, the assigned Group C queue and the architecture.
2. Add three records and identify the front, rear and batch IDs.
3. Peek at the front, update a record and show that order remains unchanged.
4. Dequeue the front and explain FIFO. Explain how deleting a selected record differs.
5. Show an adjustment with a reason and its recorded quantity and unit in Activity.
6. Show alerts, the category chart, report views and CSV/print options.
7. Restart the application and confirm saved records and queue order persist.
8. Explain the queue time complexities and limitations. Finish with conclusions.

The video must be recorded and uploaded separately. Never put private credentials or API keys in the repository or report.
