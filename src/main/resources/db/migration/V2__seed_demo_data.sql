INSERT INTO customers (id, name, email, created_at) VALUES
('11111111-1111-1111-1111-111111111111', 'Rajesh Kumar', 'rajesh@example.com', CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'Priya Sharma', 'priya@example.com', CURRENT_TIMESTAMP);

-- Order 1002 is intentionally five days late for the portfolio demo.
INSERT INTO orders (id, order_number, customer_id, total_amount, status, expected_delivery_date, delivered_date, created_at) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '1001', '11111111-1111-1111-1111-111111111111', 1299.00, 'DELIVERED', CURRENT_DATE - 10, CURRENT_DATE - 8, CURRENT_TIMESTAMP),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '1002', '11111111-1111-1111-1111-111111111111', 2499.00, 'DELAYED', CURRENT_DATE - 5, NULL, CURRENT_TIMESTAMP),
('cccccccc-cccc-cccc-cccc-cccccccccccc', '1003', '22222222-2222-2222-2222-222222222222', 799.00, 'SHIPPED', CURRENT_DATE + 2, NULL, CURRENT_TIMESTAMP);

INSERT INTO payments (id, payment_reference, order_id, amount, status, created_at) VALUES
('aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa', 'PAY-1001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1299.00, 'CAPTURED', CURRENT_TIMESTAMP),
('bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb', 'PAY-1002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 2499.00, 'CAPTURED', CURRENT_TIMESTAMP),
('cccccccc-3333-3333-3333-cccccccccccc', 'PAY-1003', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 799.00, 'CAPTURED', CURRENT_TIMESTAMP);

INSERT INTO support_tickets (id, ticket_number, customer_id, order_id, subject, description, status, created_at) VALUES
('dddddddd-dddd-dddd-dddd-dddddddddddd', 'TKT-1001', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
 'Order delayed', 'Order 1002 has not arrived and the customer is asking for a refund.', 'OPEN', CURRENT_TIMESTAMP);
