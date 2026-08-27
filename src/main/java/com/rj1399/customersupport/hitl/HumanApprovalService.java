package com.rj1399.customersupport.hitl;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HumanApprovalService {
    private static final BigDecimal HUMAN_APPROVAL_THRESHOLD = CustomerSupportService.MAX_AUTOMATIC_REFUND;

    private final JdbcTemplate jdbc;
    private final CustomerSupportService customerSupportService;

    public HumanApprovalService(JdbcTemplate jdbc, CustomerSupportService customerSupportService) {
        this.jdbc = jdbc;
        this.customerSupportService = customerSupportService;
    }

    public boolean requiresApproval(BigDecimal amount) {
        return amount.compareTo(HUMAN_APPROVAL_THRESHOLD) > 0;
    }

    @Transactional
    public Approval create(String orderNumber, BigDecimal amount, String reason, String idempotencyKey, String executionId) {
        Approval existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO human_approvals (id, order_number, amount, reason, idempotency_key, status, execution_id, created_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                id, orderNumber, amount, reason, idempotencyKey, executionId, Instant.now());
        return get(id);
    }

    public List<Approval> pending() {
        return jdbc.query("SELECT id, order_number, amount, reason, idempotency_key, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE status='PENDING' ORDER BY created_at DESC", this::map);
    }

    public Approval get(UUID id) {
        return jdbc.queryForObject("SELECT id, order_number, amount, reason, idempotency_key, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE id=?", this::map, id);
    }

    @Transactional
    public Approval approve(UUID id, String decidedBy, String decisionReason) {
        Approval current = get(id);
        if (!"PENDING".equals(current.status())) return current;
        customerSupportService.createApprovedRefund(new ApiDtos.RefundRequest(current.orderNumber(), current.reason(), "hitl-" + current.id()));
        jdbc.update("UPDATE human_approvals SET status='APPROVED', decided_at=?, decision_by=?, decision_reason=? WHERE id=? AND status='PENDING'", Instant.now(), decidedBy, decisionReason, id);
        return get(id);
    }

    @Transactional
    public Approval reject(UUID id, String decidedBy, String decisionReason) {
        Approval current = get(id);
        if (!"PENDING".equals(current.status())) return current;
        jdbc.update("UPDATE human_approvals SET status='REJECTED', decided_at=?, decision_by=?, decision_reason=? WHERE id=? AND status='PENDING'", Instant.now(), decidedBy, decisionReason, id);
        return get(id);
    }

    private Approval findByIdempotencyKey(String key) {
        List<Approval> rows = jdbc.query("SELECT id, order_number, amount, reason, idempotency_key, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE idempotency_key=?", this::map, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Approval map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        java.sql.Timestamp decided = rs.getTimestamp("decided_at");
        return new Approval(rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getBigDecimal("amount"), rs.getString("reason"), rs.getString("idempotency_key"), rs.getString("status"), rs.getString("execution_id"), rs.getTimestamp("created_at").toInstant(), decided == null ? null : decided.toInstant(), rs.getString("decision_by"), rs.getString("decision_reason"));
    }

    public record Approval(UUID id, String orderNumber, BigDecimal amount, String reason, String idempotencyKey, String status, String executionId, Instant createdAt, Instant decidedAt, String decisionBy, String decisionReason) {}
}
