package com.rj1399.customersupport.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

@Component
public class AgentTraceStore {
    private final Map<String, List<AgentTrace>> traces = new ConcurrentHashMap<>();
    private final Map<String, List<SubmissionPublisher<AgentTrace>>> subscribers = new ConcurrentHashMap<>();

    public String start() {
        String executionId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        traces.put(executionId, Collections.synchronizedList(new ArrayList<>()));
        return executionId;
    }

    public void add(AgentTrace trace) {
        traces.computeIfAbsent(trace.executionId(), ignored -> Collections.synchronizedList(new ArrayList<>())).add(trace);
        for (SubmissionPublisher<AgentTrace> publisher : subscribers.getOrDefault(trace.executionId(), List.of())) {
            publisher.submit(trace);
        }
    }

    public List<AgentTrace> get(String executionId) {
        return List.copyOf(traces.getOrDefault(executionId, List.of()));
    }

    public Flow.Publisher<AgentTrace> subscribe(String executionId) {
        SubmissionPublisher<AgentTrace> publisher = new SubmissionPublisher<>();
        subscribers.computeIfAbsent(executionId, ignored -> new CopyOnWriteArrayList<>()).add(publisher);
        for (AgentTrace trace : get(executionId)) {
            publisher.submit(trace);
        }
        return publisher;
    }

    public void complete(String executionId) {
        for (SubmissionPublisher<AgentTrace> publisher : subscribers.getOrDefault(executionId, List.of())) {
            publisher.close();
        }
        subscribers.remove(executionId);
    }
}
