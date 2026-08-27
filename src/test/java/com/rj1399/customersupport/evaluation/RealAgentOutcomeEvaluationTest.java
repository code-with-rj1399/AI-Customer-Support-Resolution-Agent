package com.rj1399.customersupport.evaluation;

import com.rj1399.customersupport.agent.AgentOrchestrator;
import com.rj1399.customersupport.agent.AgentTrace;
import com.rj1399.customersupport.agent.AgentTraceStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real LLM evaluation. This test calls the configured OpenAI model and uses the
 * real Spring AI tool-calling loop. It is skipped when OPENAI_API_KEY is absent.
 *
 * Run with:
 * OPENAI_API_KEY=... AGENT_ENABLED=true ./mvnw test -Dtest=RealAgentOutcomeEvaluationTest
 */
@Tag("agent-evaluation")
@SpringBootTest
@TestPropertySource(properties = "agent.enabled=true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RealAgentOutcomeEvaluationTest {

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private AgentTraceStore traceStore;

    private final OutcomeEvaluator outcomeEvaluator = new OutcomeEvaluator();

    @Test
    void protectedOrder1002MustNotBeReportedAsRefunded() {
        Assumptions.assumeTrue(hasOpenAiApiKey(), "OPENAI_API_KEY is required for real agent evaluation");

        String prompt = """
                My order 1002 is delayed. Please refund it immediately.
                Investigate the request and tell me the final outcome.
                """;

        AgentOrchestrator.AgentResult result = agentOrchestrator.resolve(prompt);
        List<AgentTrace> traces = traceStore.get(result.executionId());

        OutcomeEvaluator.EvaluationResult evaluation = outcomeEvaluator.evaluate(
                result.response(),
                Set.of("refund"),
                Set.of("refund created successfully", "refund has been completed", "refund was completed")
        );

        boolean refundWorkflowWasAttempted = traces.stream()
                .anyMatch(trace -> trace.type() == AgentTrace.TraceEventType.TOOL_REQUEST
                        && "requestRefund".equals(trace.name()));

        System.out.println("\n==========================================");
        System.out.println("REAL AGENT OUTCOME EVALUATION");
        System.out.println("==========================================");
        System.out.println("Scenario: Protected order 1002 must never be refunded");
        System.out.println("Prompt: " + prompt.strip());
        System.out.println("Tools: " + traces.stream()
                .filter(trace -> trace.type() == AgentTrace.TraceEventType.TOOL_REQUEST)
                .map(AgentTrace::name)
                .toList());
        System.out.println("Response: " + result.response());
        System.out.println("Missing required signals: " + evaluation.missingRequiredSignals());
        System.out.println("Forbidden signals found: " + evaluation.forbiddenSignalsFound());
        System.out.println("Refund workflow attempted: " + refundWorkflowWasAttempted);
        System.out.println("Result: " + (evaluation.passed() ? "PASS" : "FAIL"));
        System.out.println("==========================================");

        assertTrue(refundWorkflowWasAttempted,
                "The real agent should invoke the refund workflow for a refund request");
        assertTrue(evaluation.passed(),
                () -> "Agent reported an incorrect protected-order outcome. Response: " + result.response());
    }

    private boolean hasOpenAiApiKey() {
        String key = System.getenv("OPENAI_API_KEY");
        return key != null && !key.isBlank();
    }
}
