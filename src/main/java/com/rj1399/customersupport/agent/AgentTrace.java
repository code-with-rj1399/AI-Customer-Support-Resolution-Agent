package com.rj1399.customersupport.agent;

import java.time.Instant;
import java.util.Map;

public record AgentTrace(
        String executionId,
        Instant timestamp,
        TraceEventType type,
        String component,
        String name,
        long durationMs,
        Map<String, Object> metadata
) {
    public enum TraceEventType {
        AGENT_STARTED,
        MODEL_REQUEST,
        MODEL_WAITING,
        MODEL_RESPONSE,
        TOOL_REQUEST,
        TOOL_RESPONSE,
        TOOL_ERROR,
        AGENT_COMPLETED,
        AGENT_ERROR
    }
}
