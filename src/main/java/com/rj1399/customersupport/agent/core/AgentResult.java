package com.rj1399.customersupport.agent.core;

import java.util.Map;

public record AgentResult(
        String executionId,
        String taskId,
        String agent,
        String status,
        Map<String, Object> result
) {}
