BEGIN;

TRUNCATE TABLE
    order_items,
    orders,
    cart_items,
    carts,
    transactions,
    daily_sales_summary,
    products,
    categories,
    users
RESTART IDENTITY CASCADE;

INSERT INTO categories (id, name) VALUES
    (1, 'Electronics'),
    (2, 'Accessories'),
    (3, 'Home Office'),
    (4, 'Gaming'),
    (5, 'Books');

INSERT INTO products (id, name, description, price, stock, category_id, created_at, version)
SELECT
    i,
    'Req9 Checkout Product ' || i,
    'Per-thread checkout product for Requirement 9 stress testing.',
    25.00,
    100,
    ((i - 1) % 5) + 1,
    NOW(),
    0
FROM generate_series(1, 100) AS s(i);

INSERT INTO users (id, name, email, password, role, balance, created_at) VALUES
    (
        1,
        'Admin User',
        'admin@example.com',
        '$2a$10$jQbOZVGrQC46UcrqNaOgOeQ5g9xxPkX6NPU/KjVgYKFgFWQloAczG',
        'ADMIN',
        0.00,
        NOW()
    );

INSERT INTO users (id, name, email, password, role, balance, created_at)
SELECT
    i + 1,
    'Stress User ' || i,
    'shopper' || i || '@example.com',
    '$2a$10$w.VGOX6W0aCCu28P18EG7uHkuBssvZCtk5fNWSOC9zvj6XUGIvyw2',
    'USER',
    0.00,
    NOW()
FROM generate_series(1, 100) AS s(i);

INSERT INTO carts (id, user_id, created_at)
SELECT
    i,
    i + 1,
    NOW()
FROM generate_series(1, 100) AS s(i);

SELECT setval(pg_get_serial_sequence('categories', 'id'), COALESCE((SELECT MAX(id) FROM categories), 1), true);
SELECT setval(pg_get_serial_sequence('products', 'id'), COALESCE((SELECT MAX(id) FROM products), 1), true);
SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1), true);
SELECT setval(pg_get_serial_sequence('carts', 'id'), COALESCE((SELECT MAX(id) FROM carts), 1), true);

SELECT 'categories' AS table_name, COUNT(*) AS row_count FROM categories
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'users', COUNT(*) FROM users
UNION ALL
SELECT 'carts', COUNT(*) FROM carts
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'transactions', COUNT(*) FROM transactions
ORDER BY table_name;

SELECT 'total_product_stock' AS metric, COALESCE(SUM(stock), 0) AS value FROM products
UNION ALL
SELECT 'negative_stock_products', COUNT(*) FROM products WHERE stock < 0
ORDER BY metric;

COMMIT;
