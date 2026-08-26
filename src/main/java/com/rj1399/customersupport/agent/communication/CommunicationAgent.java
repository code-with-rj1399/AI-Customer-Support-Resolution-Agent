package com.rj1399.customersupport.agent.communication;

import com.rj1399.customersupport.agent.core.AgentResult;
import com.rj1399.customersupport.agent.core.AgentTask;
import com.rj1399.customersupport.agent.core.SupportAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CommunicationAgent implements SupportAgent {
    private final ChatClient chatClient;

    public CommunicationAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String name() { return "communication-agent"; }

    @Override
    public AgentResult execute(AgentTask task) {
        String context = String.valueOf(task.context().getOrDefault("resolution", ""));
        String response = chatClient.prompt()
                .system("You are a customer support communication specialist. Turn the supplied structured resolution into a concise, factual customer response. Never invent facts.")
                .user(context)
                .call()
                .content();
        return new AgentResult(task.executionId(), task.taskId(), name(), "COMPLETED", Map.of("message", response));
    }
}
