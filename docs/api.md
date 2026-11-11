# WasiuMart inventory API

Base URL: `http://localhost:8080/api`. JSON is used for requests and responses. The Vite development server forwards `/api` to the backend.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Check backend and SQLite connection. |
| GET | `/inventory` | List records in FIFO order. |
| POST | `/inventory` | Validate and enqueue a record; returns 201 and a Location header. |
| GET | `/inventory/{id}` | Read one record. |
| PUT | `/inventory/{id}` | Replace editable fields without changing its queue position or arrival time. |
| POST | `/inventory/{id}/adjust` | Change stock by a non-zero signed `delta` and optional `reason` without changing queue position. |
| DELETE | `/inventory/{id}` | Delete a selected record; this is not dequeue. |
| GET | `/queue` | Return size, front, rear and all records in order. Front and rear are null when empty. |
| POST | `/queue/dequeue` | Remove and return the front record. An optional JSON `expectedId` prevents removing a different front record if the queue has changed. |
| GET | `/dashboard` | Inventory value, record count, low and out-of-stock counts, expired and seven-day expiry counts. |
| GET | `/activity` | Last 100 changes, newest first. |

Example POST or PUT body:

```json
{
  "name": "Basmati Rice",
  "category": "Grains",
  "quantity": 25,
  "unit": "kg",
  "unitPrice": 350.00,
  "lowStockThreshold": 5,
  "expiryDate": "2027-12-15"
}
```

All fields except expiryDate are required; expiryDate can be null or an ISO date. Numeric fields must be nonnegative. ID and addedAt are assigned by the server and preserved on update. Inventory value is quantity × unitPrice. Low stock means positive quantity at or below threshold; zero is counted separately as out of stock. Expiring includes today through seven days from today, inclusive; already expired is separate. The backend uses the system's local calendar date for expiry calculations.

Malformed/invalid input returns 400, a missing record returns 404, and dequeue of an empty queue returns 409. Error bodies use `{ "error": "message" }`.

SQLite is authoritative: every service operation reconstructs the Java queue in insertion-ID order. Inventory mutations and their activity entries are committed together in a SQLite transaction; an exception rolls both back. This is a single-process local design, not a multi-user or multi-instance system. Search and filtering are not yet implemented by the API.

Stock adjustments are atomic, cannot reduce stock below zero and are logged as `STOCK_IN` or `STOCK_OUT` with signed quantity, unit and reason. Existing records without movement details remain unchanged. The dashboard and category chart show current stored inventory, not sales or profit.
