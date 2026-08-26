package com.rj1399.customersupport.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent/executions")
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class AgentTraceController {
    private final AgentTraceStore store;

    public AgentTraceController(AgentTraceStore store) {
        this.store = store;
    }

    @GetMapping(value = "/{executionId}/trace", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AgentTrace> trace(@PathVariable String executionId) {
        return store.get(executionId);
    }
}
