package com.rj1399.customersupport.agent.core;

import java.util.Map;

public record AgentTask(String executionId, String taskId, String type, Map<String, Object> context) {}
