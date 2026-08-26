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
    public String name() { return "resolution-agent"; }

    @Override
    public AgentResult execute(AgentTask task) {
        String orderNumber = String.valueOf(task.context().get("orderNumber"));
        String reason = String.valueOf(task.context().getOrDefault("reason", "Customer requested resolution"));
        String idempotencyKey = "refund-" + task.executionId() + "-" + orderNumber;

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

        // Always go through the guarded refund entry point. It performs the
        // final payment validation, amount check and HITL routing. The agent
        // must never bypass that workflow by calling createRefund directly.
        var refund = tools.requestRefund(orderNumber, reason, idempotencyKey);
        result.put("refund", refund);

        return switch (refund.status()) {
            case "COMPLETED" -> new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED", result);
            case "PENDING_HUMAN_APPROVAL" -> new AgentResult(task.executionId(), task.taskId(), name(), "WAITING_FOR_HUMAN", result);
            default -> new AgentResult(task.executionId(), task.taskId(), name(), "REJECTED", result);
        };
    }
}
