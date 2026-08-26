package com.rj1399.customersupport.agent.resolution;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import com.rj1399.customersupport.hitl.HumanApprovalService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ResolutionAgent implements SupportAgent {
    private final CustomerSupportAgentTools tools;
    private final HumanApprovalService approvals;

    public ResolutionAgent(CustomerSupportAgentTools tools, HumanApprovalService approvals) {
        this.tools = tools;
        this.approvals = approvals;
    }

    @Override
    public String name() { return "resolution-agent"; }

    @Override
    public AgentResult execute(AgentTask task) {
        String orderNumber = String.valueOf(task.context().get("orderNumber"));
        String reason = String.valueOf(task.context().getOrDefault("reason", "Customer requested resolution"));

        var knowledge = tools.searchKnowledgeBase(reason + " refund eligibility order " + orderNumber);
        var policy = tools.checkRefundPolicy(orderNumber);
        var payment = tools.getPayment(orderNumber);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policy);
        result.put("payment", payment);
        result.put("knowledgeSources", knowledge.matches().stream().map(match -> Map.of(
                "source", match.source(), "score", match.score() == null ? 0.0 : match.score())).toList());
        result.put("knowledgeContext", knowledge.context());

        if (!policy.eligible()) {
            return new AgentResult(task.executionId(), task.taskId(), name(), "REJECTED", result);
        }

        if (approvals.requiresApproval(payment.amount())) {
            String approvalIdempotencyKey = "hitl-" + task.executionId() + "-" + orderNumber;
            var approval = approvals.create(orderNumber, payment.amount(), reason, approvalIdempotencyKey, task.executionId());
            result.put("approval", approval);
            return new AgentResult(task.executionId(), task.taskId(), name(), "WAITING_FOR_HUMAN", result);
        }

        var refund = tools.createRefund(orderNumber, reason, UUID.randomUUID().toString());
        result.put("refund", refund);
        return new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED", result);
    }
}
