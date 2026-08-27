package com.rj1399.customersupport.evals;

import java.util.Set;

public record ToolSelectionResult(
        String scenarioId,
        Set<String> expectedRequired,
        Set<String> actualTools,
        Set<String> missingRequired,
        Set<String> unexpectedTools,
        Set<String> forbiddenToolsCalled,
        double requiredToolRecall,
        boolean passed
) {
}
