package com.rj1399.customersupport.agent.resolution;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ResolutionAgent implements SupportAgent {

    private final CustomerSupportAgentTools tools;

    public ResolutionAgent(CustomerSupportAgentTools tools) {
        this.tools = tools;
    }

    @Override
    public String name() {
        return "resolution-agent";
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String intent = String.valueOf(task.context().getOrDefault("intent", "REFUND"));
        String approvalId = task.context().get("approvalId") == null ? null : String.valueOf(task.context().get("approvalId"));

        if ("APPROVAL_STATUS".equals(intent)) {
            if (approvalId == null || approvalId.isBlank()) {
                return new AgentResult(
                        task.executionId(), task.taskId(), name(), "NEEDS_INPUT",
                        Map.of("message", "Please provide the human approval ID so I can check its status."));
            }

            var approval = tools.getHumanApprovalStats(approvalId);
            return new AgentResult(
                    task.executionId(), task.taskId(), name(), "COMPLETED",
                    Map.of("approval", approval));
        }

        String orderNumber = task.context().get("orderNumber") == null ? null : String.valueOf(task.context().get("orderNumber"));
        String reason = String.valueOf(task.context().getOrDefault("reason", "Customer requested resolution"));

        if (orderNumber == null || orderNumber.isBlank()) {
            return new AgentResult(
                    task.executionId(), task.taskId(), name(), "NEEDS_INPUT",
                    Map.of("message", "Please provide the order number so I can process the refund request."));
        }

        String idempotencyKey = "refund-" + task.executionId() + "-" + orderNumber;

        var knowledge = tools.searchKnowledgeBase(reason + " refund eligibility order " + orderNumber);
        var policy = tools.checkRefundPolicy(orderNumber);
        var payment = tools.getPayment(orderNumber);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policy);
        result.put("payment", payment);
        result.put("knowledgeSources", knowledge.matches().stream().map(match -> Map.of(
                "source", match.source(),
                "score", match.score() == null ? 0.0 : match.score())).toList());
        result.put("knowledgeContext", knowledge.context());

        if (!policy.eligible()) {
            return new AgentResult(task.executionId(), task.taskId(), name(), "REJECTED", result);
        }

        var refund = tools.requestRefund(orderNumber, reason, idempotencyKey);
        result.put("refund", refund);

        return switch (refund.status()) {
            case "COMPLETED" -> new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED", result);
            case "PENDING_HUMAN_APPROVAL" -> new AgentResult(task.executionId(), task.taskId(), name(), "WAITING_FOR_HUMAN", result);
            default -> new AgentResult(task.executionId(), task.taskId(), name(), "REJECTED", result);
        };
    }
}
