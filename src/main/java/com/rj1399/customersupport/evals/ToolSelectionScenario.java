package com.rj1399.customersupport.evals;

import java.util.Set;

public record ToolSelectionScenario(
        String id,
        String description,
        Set<String> requiredTools,
        Set<String> optionalTools,
        Set<String> forbiddenTools
) {
}
