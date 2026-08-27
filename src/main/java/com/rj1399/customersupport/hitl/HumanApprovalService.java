package com.rj1399.customersupport.hitl;

import com.rj1399.customersupport.agent.AgentTrace;
import com.rj1399.customersupport.agent.AgentTraceStore;
import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HumanApprovalService {
    private static final BigDecimal HUMAN_APPROVAL_THRESHOLD = new BigDecimal("1000.00");

    private final JdbcTemplate jdbc;
    private final CustomerSupportService customerSupportService;
    private final AgentTraceStore traceStore;

    public HumanApprovalService(JdbcTemplate jdbc, CustomerSupportService customerSupportService, AgentTraceStore traceStore) {
        this.jdbc = jdbc;
        this.customerSupportService = customerSupportService;
        this.traceStore = traceStore;
    }

    public boolean requiresApproval(BigDecimal amount) { return amount.compareTo(HUMAN_APPROVAL_THRESHOLD) > 0; }

    @Transactional
    public Approval create(String orderNumber, BigDecimal amount, String reason, String idempotencyKey, String executionId) {
        Approval existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;

        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO human_approvals (id, order_number, amount, reason, idempotency_key, status, execution_id, created_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                    id, orderNumber, amount, reason, idempotencyKey, executionId, Instant.now());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return findByIdempotencyKey(idempotencyKey);
        }
        Approval approval = get(id);
        if (executionId != null) {
            traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.HUMAN_APPROVAL_REQUESTED,
                    "hitl", "refund-approval", 0,
                    Map.of("approvalId", id.toString(), "orderNumber", orderNumber, "amount", amount)));
        }
        return approval;
    }

    public List<Approval> pending() {
        return jdbc.query("SELECT id, order_number, amount, reason, idempotency_key, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE status = 'PENDING' ORDER BY created_at DESC", this::map);
    }

    public Approval get(UUID id) {
        return jdbc.queryForObject("SELECT id, order_number, amount, reason, idempotency_key, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE id = ?", this::map, id);
    }

    @Transactional
    public Approval approve(UUID id, String decidedBy, String decisionReason) {
        Approval current = get(id);
        if (!"PENDING".equals(current.status())) return current;

        customerSupportService.createRefund(new ApiDtos.RefundRequest(current.orderNumber(), current.reason(), "hitl-" + current.id()));
        int updated = jdbc.update("UPDATE human_approvals SET status='APPROVED', decided_at=?, decision_by=?, decision_reason=? WHERE id=? AND status='PENDING'",
                Instant.now(), decidedBy, decisionReason, id);
        if (updated == 0) return get(id);
        publishDecision(current, "APPROVED", decidedBy);
        return get(id);
    }

    @Transactional
    public Approval reject(UUID id, String decidedBy, String decisionReason) {
        Approval current = get(id);
        if (!"PENDING".equals(current.status())) return current;
        int updated = jdbc.update("UPDATE human_approvals SET status='REJECTED', decided_at=?, decision_by=?, decision_reason=? WHERE id=? AND status='PENDING'",
                Instant.now(), decidedBy, decisionReason, id);
        if (updated == 0) return get(id);
        publishDecision(current, "REJECTED", decidedBy);
        return get(id);
    }

    private Approval findByIdempotencyKey(String idempotencyKey) {
        List<Approval> approvals = jdbc.query("SELECT id, order_number, amount, reason, idempotency_key, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE idempotency_key = ?", this::map, idempotencyKey);
        return approvals.isEmpty() ? null : approvals.getFirst();
    }

    private void publishDecision(Approval approval, String decision, String decidedBy) {
        if (approval.executionId() != null) {
            traceStore.add(new AgentTrace(approval.executionId(), Instant.now(), AgentTrace.TraceEventType.HUMAN_APPROVAL_DECISION,
                    "hitl", "refund-approval", 0,
                    Map.of("approvalId", approval.id().toString(), "decision", decision, "decidedBy", decidedBy)));
        }
    }

    private Approval map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Approval(rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getBigDecimal("amount"),
                rs.getString("reason"), rs.getString("idempotency_key"), rs.getString("status"), rs.getString("execution_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
                rs.getString("decision_by"), rs.getString("decision_reason"));
    }

    public record Approval(UUID id, String orderNumber, BigDecimal amount, String reason, String idempotencyKey, String status,
                           String executionId, Instant createdAt, Instant decidedAt, String decisionBy, String decisionReason) {}
}
