CREATE TABLE human_approvals (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    execution_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decision_by VARCHAR(120),
    decision_reason VARCHAR(500)
);

CREATE INDEX idx_human_approvals_status ON human_approvals(status);
CREATE INDEX idx_human_approvals_order ON human_approvals(order_number);
