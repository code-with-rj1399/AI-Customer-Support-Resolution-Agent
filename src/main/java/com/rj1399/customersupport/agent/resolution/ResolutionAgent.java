package com.rj1399.customersupport.agent.resolution;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import org.springframework.stereotype.Component;

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
        var policy = tools.checkRefundPolicy(orderNumber);
        if (!policy.eligible()) {
            return new AgentResult(task.executionId(), task.taskId(), name(), "REJECTED",
                    Map.of("policy", policy));
        }
        var refund = tools.createRefund(orderNumber, reason, UUID.randomUUID().toString());
        return new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED",
                Map.of("policy", policy, "refund", refund));
    }
}
