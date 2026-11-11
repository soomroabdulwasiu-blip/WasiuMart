CREATE TABLE IF NOT EXISTS inventory_records (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL, category TEXT NOT NULL, quantity TEXT NOT NULL,
 unit TEXT NOT NULL, unit_price TEXT NOT NULL, low_stock_threshold TEXT NOT NULL,
 expiry_date TEXT, added_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS inventory_activity (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 action TEXT NOT NULL, record_id INTEGER NOT NULL, product_name TEXT NOT NULL,
 occurred_at TEXT NOT NULL
);

-- Mart contacts and immutable transaction snapshots.
CREATE TABLE IF NOT EXISTS mart_customers (
 id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mart_suppliers (
 id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mart_purchases (
 id INTEGER PRIMARY KEY AUTOINCREMENT, request_key TEXT NOT NULL UNIQUE, supplier_id INTEGER NOT NULL, supplier_name TEXT NOT NULL,
 invoice_ref TEXT NOT NULL DEFAULT '', inventory_id INTEGER NOT NULL, product_name TEXT NOT NULL, quantity TEXT NOT NULL, unit TEXT NOT NULL,
 cost_price TEXT NOT NULL, total TEXT NOT NULL, occurred_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mart_sales (
 id INTEGER PRIMARY KEY AUTOINCREMENT, request_key TEXT NOT NULL UNIQUE, customer_id INTEGER, customer_name TEXT NOT NULL,
 subtotal TEXT NOT NULL, discount TEXT NOT NULL, tax_rate TEXT NOT NULL, tax_amount TEXT NOT NULL, total TEXT NOT NULL,
 payment_method TEXT NOT NULL, amount_paid TEXT NOT NULL, change_due TEXT NOT NULL, occurred_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mart_sale_items (
 id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL REFERENCES mart_sales(id), inventory_id INTEGER NOT NULL,
 product_name TEXT NOT NULL, unit TEXT NOT NULL, quantity TEXT NOT NULL, unit_price TEXT NOT NULL, line_total TEXT NOT NULL
);
