package com.rj1399.customersupport.evals;

import com.rj1399.customersupport.agent.AgentTrace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolSelectionEvaluatorTest {

    private final ToolSelectionEvaluator evaluator = new ToolSelectionEvaluator();

    @Test
    void passesWhenAllRequiredToolsAreCalledAndNoForbiddenToolIsUsed() {
        ToolSelectionScenario scenario = new ToolSelectionScenario(
                "refund-eligible",
                "Refund request",
                Set.of("getOrder", "getPayment", "checkRefundPolicy", "requestRefund"),
                Set.of("getDeliveryStatus"),
                Set.of("createRefund")
        );

        List<AgentTrace> traces = List.of(
                toolRequest("getOrder"),
                toolRequest("getPayment"),
                toolRequest("checkRefundPolicy"),
                toolRequest("requestRefund")
        );

        ToolSelectionResult result = evaluator.evaluate(scenario, traces);

        assertTrue(result.passed());
        assertEquals(1.0, result.requiredToolRecall());
        assertTrue(result.missingRequired().isEmpty());
        assertTrue(result.forbiddenToolsCalled().isEmpty());
    }

    @Test
    void failsWhenRequiredToolIsMissing() {
        ToolSelectionScenario scenario = new ToolSelectionScenario(
                "refund-missing-policy",
                "Refund request must check policy",
                Set.of("getOrder", "checkRefundPolicy", "requestRefund"),
                Set.of(),
                Set.of("createRefund")
        );

        ToolSelectionResult result = evaluator.evaluate(
                scenario,
                List.of(toolRequest("getOrder"), toolRequest("requestRefund"))
        );

        assertFalse(result.passed());
        assertEquals(Set.of("checkRefundPolicy"), result.missingRequired());
        assertEquals(2.0 / 3.0, result.requiredToolRecall(), 0.0001);
    }

    @Test
    void failsWhenForbiddenToolIsCalled() {
        ToolSelectionScenario scenario = new ToolSelectionScenario(
                "refund-no-bypass",
                "Refund must not bypass human approval workflow",
                Set.of("requestRefund"),
                Set.of(),
                Set.of("createRefund")
        );

        ToolSelectionResult result = evaluator.evaluate(
                scenario,
                List.of(toolRequest("requestRefund"), toolRequest("createRefund"))
        );

        assertFalse(result.passed());
        assertEquals(Set.of("createRefund"), result.forbiddenToolsCalled());
    }

    private AgentTrace toolRequest(String name) {
        return new AgentTrace(
                "exec_test",
                Instant.now(),
                AgentTrace.TraceEventType.TOOL_REQUEST,
                "tool",
                name,
                0,
                Map.of()
        );
    }
}
