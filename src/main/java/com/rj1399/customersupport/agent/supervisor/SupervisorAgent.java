package com.rj1399.customersupport.agent.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj1399.customersupport.agent.communication.CommunicationAgent;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import com.rj1399.customersupport.agent.order.OrderInvestigationAgent;
import com.rj1399.customersupport.agent.resolution.ResolutionAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SupervisorAgent implements SupportAgent {

    private static final String PLANNING_PROMPT = """
            You are the supervisor for a multi-agent customer support system.
            Decide which specialist agents are required for the customer's message.

            Return ONLY valid JSON in this exact shape:
            {
              "intent": "ORDER_STATUS|REFUND|APPROVAL_STATUS|GENERAL",
              "orderNumber": "string or null",
              "approvalId": "string or null",
              "reason": "short customer request summary",
              "needsInvestigation": true,
              "needsResolution": true
            }

            Rules:
            - Understand identifiers directly from the customer's natural-language message.
            - Never invent an order number or approval ID.
            - REFUND normally needs investigation and resolution.
            - ORDER_STATUS normally needs investigation but not resolution.
            - APPROVAL_STATUS needs resolution but not order investigation.
            - GENERAL needs neither specialist unless backend information is explicitly required.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final OrderInvestigationAgent orderInvestigationAgent;
    private final ResolutionAgent resolutionAgent;
    private final CommunicationAgent communicationAgent;

    public SupervisorAgent(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            OrderInvestigationAgent orderInvestigationAgent,
            ResolutionAgent resolutionAgent,
            CommunicationAgent communicationAgent) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.orderInvestigationAgent = orderInvestigationAgent;
        this.resolutionAgent = resolutionAgent;
        this.communicationAgent = communicationAgent;
    }

    @Override
    public String name() {
        return "supervisor-agent";
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String message = String.valueOf(task.context().get("message"));
        Plan plan = createPlan(message);

        System.out.println(plan.toString());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customerMessage", message);
        context.put("plan", plan);

        if (plan.needsInvestigation()) {
            if (plan.orderNumber() == null || plan.orderNumber().isBlank()) {
                context.put("investigation", "Order number was not provided.");
            } else {
                AgentResult investigation = orderInvestigationAgent.execute(new AgentTask(
                        task.executionId(),
                        UUID.randomUUID().toString(),
                        "ORDER_INVESTIGATION",
                        Map.of("orderNumber", plan.orderNumber())));
                context.put("investigation", investigation.result());
            }
        }

        if (plan.needsResolution()) {
            Map<String, Object> resolutionContext = new LinkedHashMap<>();
            resolutionContext.put("intent", plan.intent());
            resolutionContext.put("reason", plan.reason());
            if (plan.orderNumber() != null) {
                resolutionContext.put("orderNumber", plan.orderNumber());
            }
            if (plan.approvalId() != null) {
                resolutionContext.put("approvalId", plan.approvalId());
            }

            AgentResult resolution = resolutionAgent.execute(new AgentTask(
                    task.executionId(),
                    UUID.randomUUID().toString(),
                    "RESOLUTION",
                    resolutionContext));
            context.put("resolution", resolution.result());
            context.put("resolutionStatus", resolution.status());
        }

        AgentResult communication = communicationAgent.execute(new AgentTask(
                task.executionId(),
                UUID.randomUUID().toString(),
                "COMMUNICATION",
                Map.of("resolution", context)));

        return new AgentResult(
                task.executionId(),
                task.taskId(),
                name(),
                "COMPLETED",
                Map.of(
                        "response", communication.result().getOrDefault("message", ""),
                        "plan", plan,
                        "workflow", context
                )
        );
    }

    private Plan createPlan(String message) {
        String json = chatClient.prompt()
                .system(PLANNING_PROMPT)
                .user(message)
                .call()
                .content();

        try {
            return objectMapper.readValue(json, Plan.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Supervisor could not create a valid multi-agent plan", ex);
        }
    }

    public record Plan(
            String intent,
            String orderNumber,
            String approvalId,
            String reason,
            boolean needsInvestigation,
            boolean needsResolution) {
    }
}
