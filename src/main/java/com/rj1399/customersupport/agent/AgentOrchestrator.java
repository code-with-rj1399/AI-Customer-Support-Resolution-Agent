package com.rj1399.customersupport.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class AgentOrchestrator {

    private static final String SYSTEM_PROMPT = """
            You are the Customer Support Supervisor Agent.

            Use backend tools whenever the answer depends on system state.

            For every new refund request:
            1. Investigate the order.
            2. Check delivery status.
            3. Check payment status.
            4. Check refund eligibility.
            5. Call requestRefund instead of createRefund.

            If requestRefund returns PENDING_HUMAN_APPROVAL:
            - Do not claim that the refund is complete.
            - Tell the customer that the refund is waiting for human approval.
            - Include the approval ID in the response.

            If the customer asks about a previous human approval, use
            getHumanApprovalStats with the approval ID and report the
            current status.

            Never bypass backend business rules or human approval.
            Keep responses concise and customer-friendly.
            """;

    private final ChatClient chatClient;
    private final CustomerSupportAgentTools tools;
    private final AgentTraceStore traceStore;
    private final PromptInjectionGuardrail inputGuardrail;

    public AgentOrchestrator(
            ChatClient.Builder chatClientBuilder,
            CustomerSupportAgentTools tools,
            AgentTraceStore traceStore) {
        this.chatClient = chatClientBuilder.build();
        this.tools = tools;
        this.traceStore = traceStore;
        this.inputGuardrail = inputGuardrail;
    }

    public AgentResult resolve(String message) {
        return resolve(message, traceStore.start());
    }

    public AgentResult resolve(String message, String executionId) {
        CustomerSupportAgentTools.bindExecution(executionId);

        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(tools)
                    .call()
                    .content();

            return new AgentResult(
                    executionId,
                    response == null ? "" : response
            );
        } finally {
            CustomerSupportAgentTools.clearExecution();
            traceStore.complete(executionId);
        }
    }

    public record AgentResult(
            String executionId,
            String response) {
    }
}
