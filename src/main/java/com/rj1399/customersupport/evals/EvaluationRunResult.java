package com.rj1399.customersupport.evals;

import java.util.List;

public record EvaluationRunResult(
        String scenarioId,
        String executionId,
        String response,
        ToolSelectionResult toolSelection,
        List<String> traceNames
) {
}
