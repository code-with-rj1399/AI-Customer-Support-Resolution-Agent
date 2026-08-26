package com.rj1399.customersupport.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentTraceStore traceStore;

    public AgentController(AgentOrchestrator orchestrator, AgentTraceStore traceStore) {
        this.orchestrator = orchestrator;
        this.traceStore = traceStore;
    }

    @PostMapping(value = "/resolve", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentResponse resolve(@Valid @RequestBody AgentRequest request) {
        AgentOrchestrator.AgentResult result = orchestrator.resolve(request.message());
        return new AgentResponse(result.executionId(), result.response(), traceStore.get(result.executionId()));
    }

    public record AgentRequest(@NotBlank(message = "message must not be blank") String message) {}

    public record AgentResponse(String executionId, String response, List<AgentTrace> trace) {}
}
