package com.rj1399.customersupport.agent.core;

public interface SupportAgent {
    String name();
    AgentResult execute(AgentTask task);
}
