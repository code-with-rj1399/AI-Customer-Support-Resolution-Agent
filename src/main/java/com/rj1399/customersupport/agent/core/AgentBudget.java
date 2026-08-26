package com.rj1399.customersupport.agent.core;

public record AgentBudget(int maxAgentDelegations, int maxToolCalls) {
    public static AgentBudget defaults() {
        return new AgentBudget(3, 10);
    }
}
