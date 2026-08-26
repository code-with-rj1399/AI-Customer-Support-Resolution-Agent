-- Additional demo data for the Human-in-the-Loop portfolio flow.
-- Cases intentionally cover happy paths, rejection paths, payment issues,
-- high-value refunds, delivered orders, and active shipments.

INSERT INTO customers (id, name, email, created_at) VALUES
('33333333-3333-3333-3333-333333333333', 'Arjun Mehta', 'arjun@example.com', CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444444', 'Sneha Reddy', 'sneha@example.com', CURRENT_TIMESTAMP),
('55555555-5555-5555-5555-555555555555', 'Vikram Rao', 'vikram@example.com', CURRENT_TIMESTAMP),
('66666666-6666-6666-6666-666666666666', 'Ananya Iyer', 'ananya@example.com', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO orders (id, order_number, customer_id, total_amount, status, expected_delivery_date, delivered_date, created_at) VALUES
('dddddddd-dddd-dddd-dddd-dddddddddddd', '1004', '33333333-3333-3333-3333-333333333333', 499.00, 'DELAYED', CURRENT_DATE - 1, NULL, CURRENT_TIMESTAMP),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '1005', '33333333-3333-3333-3333-333333333333', 1899.00, 'DELAYED', CURRENT_DATE - 4, NULL, CURRENT_TIMESTAMP),
('ffffffff-ffff-ffff-ffff-ffffffffffff', '1006', '44444444-4444-4444-4444-444444444444', 7499.00, 'DELAYED', CURRENT_DATE - 7, NULL, CURRENT_TIMESTAMP),
('10101010-1010-1010-1010-101010101010', '1007', '44444444-4444-4444-4444-444444444444', 1299.00, 'SHIPPED', CURRENT_DATE + 1, NULL, CURRENT_TIMESTAMP),
('20202020-2020-2020-2020-202020202020', '1008', '55555555-5555-5555-5555-555555555555', 3499.00, 'DELIVERED', CURRENT_DATE - 6, CURRENT_DATE - 3, CURRENT_TIMESTAMP),
('30303030-3030-3030-3030-303030303030', '1009', '55555555-5555-5555-5555-555555555555', 599.00, 'DELIVERED', CURRENT_DATE - 2, CURRENT_DATE - 2, CURRENT_TIMESTAMP),
('40404040-4040-4040-4040-404040404040', '1010', '66666666-6666-6666-6666-666666666666', 2199.00, 'CANCELLED', CURRENT_DATE + 3, NULL, CURRENT_TIMESTAMP),
('50505050-5050-5050-5050-505050505050', '1011', '66666666-6666-6666-6666-666666666666', 899.00, 'DELAYED', CURRENT_DATE - 2, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, payment_reference, order_id, amount, status, created_at) VALUES
('44444444-1111-1111-1111-444444444444', 'PAY-1004', 'dddddddd-dddd-dddd-dddd-dddddddddddd', 499.00, 'CAPTURED', CURRENT_TIMESTAMP),
('55555555-2222-2222-2222-555555555555', 'PAY-1005', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 1899.00, 'CAPTURED', CURRENT_TIMESTAMP),
('66666666-3333-3333-3333-666666666666', 'PAY-1006', 'ffffffff-ffff-ffff-ffff-ffffffffffff', 7499.00, 'CAPTURED', CURRENT_TIMESTAMP),
('77777777-4444-4444-4444-777777777777', 'PAY-1007', '10101010-1010-1010-1010-101010101010', 1299.00, 'CAPTURED', CURRENT_TIMESTAMP),
('88888888-5555-5555-5555-888888888888', 'PAY-1008', '20202020-2020-2020-2020-202020202020', 3499.00, 'CAPTURED', CURRENT_TIMESTAMP),
('99999999-6666-6666-6666-999999999999', 'PAY-1009', '30303030-3030-3030-3030-303030303030', 599.00, 'CAPTURED', CURRENT_TIMESTAMP),
('abababab-7777-7777-7777-abababababab', 'PAY-1010', '40404040-4040-4040-4040-404040404040', 2199.00, 'REFUNDED', CURRENT_TIMESTAMP),
('bcbcbcbc-8888-8888-8888-bcbcbcbcbcbc', 'PAY-1011', '50505050-5050-5050-5050-505050505050', 899.00, 'CAPTURED', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO support_tickets (id, ticket_number, customer_id, order_id, subject, description, status, created_at) VALUES
('11111111-aaaa-aaaa-aaaa-111111111111', 'TKT-1002', '33333333-3333-3333-3333-333333333333', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Four day delivery delay', 'Customer requests a refund because order 1005 is four days late.', 'OPEN', CURRENT_TIMESTAMP),
('22222222-bbbb-bbbb-bbbb-222222222222', 'TKT-1003', '44444444-4444-4444-4444-444444444444', 'ffffffff-ffff-ffff-ffff-ffffffffffff', 'High value delayed order', 'Customer requests a refund for a seven day delayed order. Amount requires human approval.', 'OPEN', CURRENT_TIMESTAMP),
('33333333-cccc-cccc-cccc-333333333333', 'TKT-1004', '55555555-5555-5555-5555-555555555555', '20202020-2020-2020-2020-202020202020', 'Delivered late', 'Customer received the order three days after the expected date and asks about compensation.', 'OPEN', CURRENT_TIMESTAMP),
('44444444-dddd-dddd-dddd-444444444444', 'TKT-1005', '66666666-6666-6666-6666-666666666666', '40404040-4040-4040-4040-404040404040', 'Cancelled order', 'Customer wants to confirm the refund status for a cancelled order.', 'OPEN', CURRENT_TIMESTAMP),
('55555555-eeee-eeee-eeee-555555555555', 'TKT-1006', '66666666-6666-6666-6666-666666666666', '50505050-5050-5050-5050-505050505050', 'Two day delay', 'Customer asks whether a two day delayed order qualifies for an automatic refund.', 'OPEN', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO refunds (id, refund_reference, idempotency_key, order_id, amount, reason, status, created_at) VALUES
('12121212-1212-1212-1212-121212121212', 'RF-1008', 'demo-order-1008-refund', '20202020-2020-2020-2020-202020202020', 3499.00, 'Late delivery compensation', 'COMPLETED', CURRENT_TIMESTAMP),
('13131313-1313-1313-1313-131313131313', 'RF-1010', 'demo-order-1010-refund', '40404040-4040-4040-4040-404040404040', 2199.00, 'Order cancellation', 'COMPLETED', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
