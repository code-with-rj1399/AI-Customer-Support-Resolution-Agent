package com.rj1399.customersupport.agent.order;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OrderInvestigationAgent implements SupportAgent {
    private final CustomerSupportAgentTools tools;

    public OrderInvestigationAgent(CustomerSupportAgentTools tools) {
        this.tools = tools;
    }

    @Override
    public String name() { return "order-investigation-agent"; }

    @Override
    public AgentResult execute(AgentTask task) {
        String orderNumber = String.valueOf(task.context().get("orderNumber"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", tools.getOrder(orderNumber));
        result.put("delivery", tools.getDeliveryStatus(orderNumber));
        result.put("payment", tools.getPayment(orderNumber));
        return new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED", result);
    }
}
