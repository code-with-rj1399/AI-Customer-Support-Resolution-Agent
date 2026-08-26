package com.rj1399.customersupport.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentTraceStore {
    private final Map<String, List<AgentTrace>> traces = new ConcurrentHashMap<>();

    public String start() {
        String executionId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        traces.put(executionId, Collections.synchronizedList(new ArrayList<>()));
        return executionId;
    }

    public void add(AgentTrace trace) {
        traces.computeIfAbsent(trace.executionId(), ignored -> Collections.synchronizedList(new ArrayList<>())).add(trace);
    }

    public List<AgentTrace> get(String executionId) {
        return List.copyOf(traces.getOrDefault(executionId, List.of()));
    }
}
