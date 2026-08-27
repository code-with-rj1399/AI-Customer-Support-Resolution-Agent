package com.rj1399.customersupport.evals;

import com.rj1399.customersupport.agent.AgentTrace;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ToolSelectionEvaluator {

    public ToolSelectionResult evaluate(
            ToolSelectionScenario scenario,
            List<AgentTrace> traces) {

        Set<String> actualTools = traces.stream()
                .filter(trace -> trace.type() == AgentTrace.TraceEventType.TOOL_REQUEST)
                .map(AgentTrace::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> missingRequired = new LinkedHashSet<>(scenario.requiredTools());
        missingRequired.removeAll(actualTools);

        Set<String> forbiddenToolsCalled = new LinkedHashSet<>(scenario.forbiddenTools());
        forbiddenToolsCalled.retainAll(actualTools);

        Set<String> allowedTools = new LinkedHashSet<>(scenario.requiredTools());
        allowedTools.addAll(scenario.optionalTools());

        Set<String> unexpectedTools = new LinkedHashSet<>(actualTools);
        unexpectedTools.removeAll(allowedTools);

        double requiredToolRecall = scenario.requiredTools().isEmpty()
                ? 1.0
                : (double) (scenario.requiredTools().size() - missingRequired.size())
                / scenario.requiredTools().size();

        boolean passed = missingRequired.isEmpty() && forbiddenToolsCalled.isEmpty();

        return new ToolSelectionResult(
                scenario.id(),
                Set.copyOf(scenario.requiredTools()),
                Set.copyOf(actualTools),
                Set.copyOf(missingRequired),
                Set.copyOf(unexpectedTools),
                Set.copyOf(forbiddenToolsCalled),
                requiredToolRecall,
                passed
        );
    }
}
