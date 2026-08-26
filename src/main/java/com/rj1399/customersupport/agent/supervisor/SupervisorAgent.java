package com.rj1399.customersupport.agent.supervisor;

import com.rj1399.customersupport.agent.communication.CommunicationAgent;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import com.rj1399.customersupport.agent.order.OrderInvestigationAgent;
import com.rj1399.customersupport.agent.resolution.ResolutionAgent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class SupervisorAgent implements SupportAgent {
    private final OrderInvestigationAgent orderAgent;
    private final ResolutionAgent resolutionAgent;
    private final CommunicationAgent communicationAgent;

    public SupervisorAgent(OrderInvestigationAgent orderAgent, ResolutionAgent resolutionAgent,
                           CommunicationAgent communicationAgent) {
        this.orderAgent = orderAgent;
        this.resolutionAgent = resolutionAgent;
        this.communicationAgent = communicationAgent;
    }

    @Override
    public String name() { return "supervisor-agent"; }

    @Override
    public AgentResult execute(AgentTask task) {
        String orderNumber = extractOrderNumber(String.valueOf(task.context().get("message")));
        if (orderNumber == null) {
            return new AgentResult(task.executionId(), task.taskId(), name(), "NEEDS_INFORMATION",
                    Map.of("message", "Please provide an order number so I can investigate the request."));
        }

        AgentResult investigation = orderAgent.execute(new AgentTask(task.executionId(), UUID.randomUUID().toString(),
                "ORDER_INVESTIGATION", Map.of("orderNumber", orderNumber)));

        String message = String.valueOf(task.context().get("message"));
        AgentResult resolution = resolutionAgent.execute(new AgentTask(task.executionId(), UUID.randomUUID().toString(),
                "RESOLUTION", Map.of("orderNumber", orderNumber, "reason", message)));

        AgentResult communication = communicationAgent.execute(new AgentTask(task.executionId(), UUID.randomUUID().toString(),
                "COMMUNICATION", Map.of("resolution", resolution.result(), "investigation", investigation.result())));

        return new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED",
                Map.of("investigation", investigation.result(), "resolution", resolution.result(),
                        "response", communication.result().get("message")));
    }

    private String extractOrderNumber(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(?:order\\s*)?(\\d{4,})\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
