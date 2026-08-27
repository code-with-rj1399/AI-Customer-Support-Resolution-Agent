package com.rj1399.customersupport.agent.supervisor;

import com.rj1399.customersupport.agent.CustomerSupportAgentTools;
import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SupervisorAgent implements SupportAgent {

    private static final String SYSTEM_PROMPT = """
            You are the supervisor for a multi-agent customer support system.

            The customer message is the source of the request. Do not use Java-side
            parsing, regex extraction, keyword routing, or assumptions about IDs.
            Interpret the message yourself and decide which available tools are needed.

            Rules:
            1. Whenever information depends on backend state, use the appropriate tool.
            2. For a refund request, investigate the relevant order, delivery status,
               payment status, and refund policy before calling requestRefund.
            3. For human approval status requests, use getHumanApprovalStats with the
               approval ID provided by the customer.
            4. If requestRefund returns PENDING_HUMAN_APPROVAL, do not claim that the
               refund is complete. Clearly state that human approval is pending and
               include the approval ID.
            5. Never call createRefund directly to bypass the guarded refund workflow.
            6. Backend tools are authoritative. Never invent order, payment, refund,
               approval, or policy data.
            7. If the customer has not supplied the identifier required by a tool,
               ask for that identifier instead of guessing.
            8. Return a concise, customer-friendly answer.
            """;

    private final ChatClient chatClient;
    private final CustomerSupportAgentTools tools;

    public SupervisorAgent(ChatClient.Builder chatClientBuilder,
                           CustomerSupportAgentTools tools) {
        this.chatClient = chatClientBuilder.build();
        this.tools = tools;
    }

    @Override
    public String name() {
        return "supervisor-agent";
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String message = String.valueOf(task.context().get("message"));

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(tools)
                .call()
                .content();

        return new AgentResult(
                task.executionId(),
                task.taskId(),
                name(),
                "COMPLETED",
                Map.of("response", response == null ? "" : response)
        );
    }
}
