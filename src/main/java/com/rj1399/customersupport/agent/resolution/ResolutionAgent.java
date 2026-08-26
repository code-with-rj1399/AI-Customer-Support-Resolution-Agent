package com.rj1399.customersupport.agent.resolution;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

        // RAG provides policy context for grounded explanations. The deterministic
        // backend policy remains authoritative for whether a refund is actually allowed.
        var knowledge = tools.searchKnowledgeBase(reason + " refund eligibility order " + orderNumber);
        var policy = tools.checkRefundPolicy(orderNumber);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policy);
        result.put("knowledgeSources", knowledge.matches().stream().map(match -> Map.of(
                "source", match.source(),
                "score", match.score() == null ? 0.0 : match.score()
        )).toList());
        result.put("knowledgeContext", knowledge.context());

        if (!policy.eligible()) {
            return new AgentResult(task.executionId(), task.taskId(), name(), "REJECTED", result);
        }

        var refund = tools.createRefund(orderNumber, reason, UUID.randomUUID().toString());
        result.put("refund", refund);
        return new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED", result);
    }
}
