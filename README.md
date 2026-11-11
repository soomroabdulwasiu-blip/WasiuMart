# WasiuMart

WasiuMart is a grocery inventory and local mart management system built with React, Java Spring Boot, SQLite, and a custom Java FIFO queue. It was developed for the SW 212 Data Structures and Algorithms project, Group C.

## Features

- Add, view, edit, search and delete inventory batches; review low-stock and expiry alerts.
- Visualize arrival order and demonstrate queue operations including enqueue, peek and dequeue.
- Maintain supplier and customer records, receive purchases, record sales and print receipts.
- Review stock activity and reports, including CSV export.

## Technology

- **Frontend:** React and Vite
- **Backend:** Java 21 and Spring Boot
- **Database:** SQLite through JDBC (no separate SQLite server is required)
- **Data structure:** custom singly linked Java queue; SQLite stores records persistently

## Setup from a fresh clone

Install Java 21, Maven 3.9+, and Node.js 20+ with npm. Internet access is needed to download dependencies on the first setup. Open this folder in VS Code or a terminal.

1. Build the frontend:

   ```powershell
   cd frontend
   npm install
   npm run build
   cd ..
   ```

2. Start Spring Boot:

   ```powershell
   cd backend
   mvn spring-boot:run
   ```

3. Open [http://127.0.0.1:8080/](http://127.0.0.1:8080/). The frontend build is served by Spring Boot. The health endpoint is [http://127.0.0.1:8080/api/health](http://127.0.0.1:8080/api/health).

The backend creates a new, empty `backend/wasiumart.db` when started from the `backend` directory if no database exists. Database files are intentionally excluded from Git. To use existing business data, restore the intended database from a trusted backup instead of overwriting it. Keep backups secure and use a SQLite-aware backup while the application is running, or copy the database after a clean shutdown.

For frontend development with live reload, start the backend as above, then open a second terminal and run `cd frontend`, `npm install`, and `npm run dev`. Open the Vite URL shown in the terminal. Vite forwards API requests to the backend.

### Windows launcher

After the frontend has been built and the database has been created or restored, `Start-WasiuMart.cmd` starts the backend and opens the application. The launcher deliberately refuses to create a missing database. Server output is written to the local `logs` folder.

## Tests

From the `backend` directory run:

```powershell
mvn test
```

The backend tests cover queue behavior, inventory operations, purchase and billing validation, and transaction rollback. To check the frontend build, run `npm run build` in `frontend`.

## Queue design

Each inventory entry represents an arrival batch. The service loads records from SQLite in ascending ID order and reconstructs a linked-node queue. Enqueue adds at the rear; peek examines the front; dequeue removes the front. Updating a record preserves its queue position. Deleting a selected record is a separate CRUD operation and is not FIFO dequeue. Queue order follows arrival, not expiry.

In the in-memory queue, peek and dequeue are O(1). Enqueue is O(n) because it checks for duplicate IDs; traversal, search, update and selected deletion are O(n). Rebuilding n records takes O(n²) in the current implementation. See [queue design](docs/queue-design.md) and [API documentation](docs/api.md).

## Scope and limitations

The application targets a single local computer and one backend process. Billing displays batches in arrival order but the cashier selects the batch; automatic FIFO allocation is not enforced. Tax defaults to zero and is manually entered per bill. Cash and card are recorded as payment methods; no payment terminal is integrated. Returns, credit balances, staff permissions, barcode catalogue, off-site backup and multi-computer synchronization are not implemented. Verify prices, tax, receipts, printer output and backup recovery before real business use.
