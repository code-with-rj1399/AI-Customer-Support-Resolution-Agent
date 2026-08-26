package com.rj1399.customersupport.agent.supervisor;

import com.rj1399.customersupport.agent.AgentTrace;
import com.rj1399.customersupport.agent.AgentTraceStore;
import com.rj1399.customersupport.agent.core.AgentTask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/multi-agent")
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class MultiAgentController {
    private final SupervisorAgent supervisor;
    private final AgentTraceStore traceStore;

    public MultiAgentController(SupervisorAgent supervisor, AgentTraceStore traceStore) {
        this.supervisor = supervisor;
        this.traceStore = traceStore;
    }

    @PostMapping("/resolve")
    public Object resolve(@Valid @RequestBody Request request) {
        String executionId = traceStore.start();
        traceStore.add(new AgentTrace(executionId, java.time.Instant.now(), AgentTrace.TraceEventType.AGENT_STARTED,
                "supervisor-agent", "resolve", 0, Map.of("messageLength", request.message().length())));
        var result = supervisor.execute(new AgentTask(executionId, UUID.randomUUID().toString(), "SUPERVISOR",
                Map.of("message", request.message())));
        traceStore.complete(executionId);
        return Map.of("executionId", executionId, "agent", result.agent(), "status", result.status(), "result", result.result(),
                "trace", traceStore.get(executionId));
    }

    public record Request(@NotBlank String message) {}
}
