package com.rj1399.customersupport.evaluation;

import com.rj1399.customersupport.agent.AgentTrace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates tool-selection behavior using the real agent trace.
 *
 * <p>This class deliberately evaluates observable tool calls rather than
 * model reasoning. Hidden model reasoning is neither captured nor required.</p>
 */
public class ToolSelectionEvaluator {

    public EvaluationResult evaluate(List<AgentTrace> traces,
                                     Set<String> requiredTools,
                                     Set<String> forbiddenTools) {
        Set<String> actualTools = traces.stream()
                .filter(trace -> trace.type() == AgentTrace.TraceEventType.TOOL_REQUEST)
                .map(AgentTrace::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> missingRequired = new LinkedHashSet<>(requiredTools);
        missingRequired.removeAll(actualTools);

        Set<String> forbiddenCalled = new LinkedHashSet<>(forbiddenTools);
        forbiddenCalled.retainAll(actualTools);

        return new EvaluationResult(
                missingRequired.isEmpty() && forbiddenCalled.isEmpty(),
                actualTools,
                missingRequired,
                forbiddenCalled
        );
    }

    public record EvaluationResult(
            boolean passed,
            Set<String> actualTools,
            Set<String> missingRequiredTools,
            Set<String> forbiddenToolsCalled
    ) {
    }
}
