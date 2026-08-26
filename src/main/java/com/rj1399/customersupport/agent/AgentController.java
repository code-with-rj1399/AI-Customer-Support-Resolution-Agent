package com.rj1399.customersupport.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    @PostMapping(value = "/resolve/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter resolveStream(@Valid @RequestBody AgentRequest request) {
        String executionId = traceStore.start();
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                AgentOrchestrator.AgentResult result = orchestrator.resolve(request.message(), executionId);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("result")
                        .data(new AgentResponse(result.executionId(), result.response(), traceStore.get(result.executionId()))));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    emitter.complete();
                }
            }
        });

        traceStore.subscribe(executionId).subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            private java.util.concurrent.Flow.Subscription subscription;

            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AgentTrace trace) {
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("trace")
                            .data(trace));
                } catch (Exception ex) {
                    subscription.cancel();
                    emitter.completeWithError(ex);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                emitter.completeWithError(throwable);
            }

            @Override
            public void onComplete() {
                // The final result event completes the HTTP stream.
            }
        });

        emitter.onCompletion(() -> traceStore.complete(executionId));
        emitter.onTimeout(() -> traceStore.complete(executionId));
        emitter.onError(error -> traceStore.complete(executionId));
        return emitter;
    }

    public record AgentRequest(@NotBlank(message = "message must not be blank") String message) {}

    public record AgentResponse(String executionId, String response, List<AgentTrace> trace) {}
}
