SELECT
    COUNT(*) AS negative_stock_products
FROM products
WHERE stock < 0;

SELECT
    COUNT(*) AS orders_after_test
FROM orders;

SELECT
    COUNT(*) AS purchase_transactions_after_test
FROM transactions
WHERE type = 'PURCHASE';

SELECT
    COALESCE(SUM(stock), 0) AS total_stock_after_test
FROM products;

SELECT
    (
        SELECT COUNT(*)
        FROM orders
    ) - (
        SELECT COUNT(*)
        FROM transactions
        WHERE type = 'PURCHASE'
    ) AS order_purchase_transaction_difference;

SELECT
    p.id,
    p.name,
    p.stock
FROM products p
ORDER BY p.id
LIMIT 10;
