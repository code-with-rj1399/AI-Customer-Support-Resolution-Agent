package com.rj1399.customersupport.evals;

import com.rj1399.customersupport.agent.AgentOrchestrator;
import com.rj1399.customersupport.agent.AgentTrace;
import com.rj1399.customersupport.agent.AgentTraceStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvaluationRunner {

    private final AgentOrchestrator agentOrchestrator;
    private final AgentTraceStore traceStore;
    private final ToolSelectionEvaluator toolSelectionEvaluator;

    public EvaluationRunner(
            AgentOrchestrator agentOrchestrator,
            AgentTraceStore traceStore,
            ToolSelectionEvaluator toolSelectionEvaluator) {
        this.agentOrchestrator = agentOrchestrator;
        this.traceStore = traceStore;
        this.toolSelectionEvaluator = toolSelectionEvaluator;
    }

    public EvaluationRunResult run(
            String customerMessage,
            ToolSelectionScenario scenario) {

        AgentOrchestrator.AgentResult agentResult =
                agentOrchestrator.resolve(customerMessage);

        List<AgentTrace> traces = traceStore.get(agentResult.executionId());

        ToolSelectionResult toolSelection =
                toolSelectionEvaluator.evaluate(scenario, traces);

        List<String> traceNames = traces.stream()
                .map(trace -> trace.type() + ":" + trace.name())
                .toList();

        return new EvaluationRunResult(
                scenario.id(),
                agentResult.executionId(),
                agentResult.response(),
                toolSelection,
                traceNames
        );
    }
}
