package com.rj1399.customersupport.evaluation;

import com.rj1399.customersupport.agent.AgentOrchestrator;
import com.rj1399.customersupport.agent.AgentTraceStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real LLM evaluation.
 *
 * <p>This test invokes the actual Spring AI agent and therefore calls the
 * configured OpenAI model. It is intentionally disabled unless OPENAI_API_KEY
 * is present.</p>
 */
@Tag("agent-evaluation")
@SpringBootTest(properties = {
        "agent.enabled=true",
        "rag.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RealAgentToolSelectionEvaluationTest {

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private AgentTraceStore traceStore;

    private final ToolSelectionEvaluator evaluator = new ToolSelectionEvaluator();

    @Test
    void shouldSelectOrderInvestigationToolsForRealModel() {
        String prompt = """
                Where is my order 1001? Please check its current delivery status.
                """;

        AgentOrchestrator.AgentResult result = agentOrchestrator.resolve(prompt);

        ToolSelectionEvaluator.EvaluationResult evaluation = evaluator.evaluate(
                traceStore.get(result.executionId()),
                Set.of("getOrder", "getDeliveryStatus"),
                Set.of("requestRefund")
        );

        System.out.println("\n================================================");
        System.out.println("REAL SPRING AI AGENT TOOL SELECTION EVALUATION");
        System.out.println("================================================");
        System.out.println("Scenario: Order tracking");
        System.out.println("Prompt: " + prompt.strip());
        System.out.println("Execution ID: " + result.executionId());
        System.out.println("Actual tools: " + evaluation.actualTools());
        System.out.println("Missing required tools: " + evaluation.missingRequiredTools());
        System.out.println("Forbidden tools called: " + evaluation.forbiddenToolsCalled());
        System.out.println("Agent response: " + result.response());
        System.out.println("Result: " + (evaluation.passed() ? "PASS" : "FAIL"));
        System.out.println("================================================\n");

        assertTrue(evaluation.passed(), () ->
                "Real agent selected unexpected tools. actual=" + evaluation.actualTools()
                        + ", missing=" + evaluation.missingRequiredTools()
                        + ", forbidden=" + evaluation.forbiddenToolsCalled());
    }
}
