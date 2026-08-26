package com.rj1399.customersupport.hitl;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
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
    private final CustomerSupportAgentTools tools;

    public HumanApprovalService(JdbcTemplate jdbc, CustomerSupportAgentTools tools) {
        this.jdbc = jdbc;
        this.tools = tools;
    }

    public boolean requiresApproval(BigDecimal amount) {
        return amount.compareTo(HUMAN_APPROVAL_THRESHOLD) > 0;
    }

    @Transactional
    public Approval create(String orderNumber, BigDecimal amount, String reason, String executionId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO human_approvals
                (id, order_number, amount, reason, status, execution_id, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                """, id, orderNumber, amount, reason, executionId, Instant.now());
        return get(id);
    }

    public List<Approval> pending() {
        return jdbc.query("SELECT id, order_number, amount, reason, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE status = 'PENDING' ORDER BY created_at DESC", this::map);
    }

    public Approval get(UUID id) {
        return jdbc.queryForObject("SELECT id, order_number, amount, reason, status, execution_id, created_at, decided_at, decision_by, decision_reason FROM human_approvals WHERE id = ?", this::map, id);
    }

    @Transactional
    public Approval approve(UUID id, String decidedBy, String decisionReason) {
        Approval current = get(id);
        if (!"PENDING".equals(current.status())) return current;
        tools.createRefund(current.orderNumber(), current.reason(), "hitl-" + current.id());
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

    private Approval map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Approval(rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getBigDecimal("amount"), rs.getString("reason"), rs.getString("status"), rs.getString("execution_id"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(), rs.getString("decision_by"), rs.getString("decision_reason"));
    }

    public record Approval(UUID id, String orderNumber, BigDecimal amount, String reason, String status, String executionId, Instant createdAt, Instant decidedAt, String decisionBy, String decisionReason) {}
}
