package com.rj1399.customersupport.evaluation;

import java.util.Locale;
import java.util.Set;

/**
 * Evaluates whether a real agent response reached the expected business outcome.
 * This evaluator intentionally checks outcomes, not exact wording, because LLM
 * responses are non-deterministic.
 */
public final class OutcomeEvaluator {

    public EvaluationResult evaluate(String response, Set<String> requiredSignals, Set<String> forbiddenSignals) {
        String normalized = response == null ? "" : response.toLowerCase(Locale.ROOT);

        Set<String> missing = requiredSignals.stream()
                .filter(signal -> !normalized.contains(signal.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toSet());

        Set<String> violations = forbiddenSignals.stream()
                .filter(signal -> normalized.contains(signal.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toSet());

        return new EvaluationResult(missing.isEmpty() && violations.isEmpty(), response, missing, violations);
    }

    public record EvaluationResult(
            boolean passed,
            String response,
            Set<String> missingRequiredSignals,
            Set<String> forbiddenSignalsFound) {
    }
}
