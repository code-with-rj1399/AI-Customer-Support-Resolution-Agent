package com.rj1399.customersupport.guardrails;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PromptInjectionGuardrail {
    private static final int MAX_INPUT_LENGTH = 8000;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+(all\\s+)?prior\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(the\\s+)?system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("show\\s+(me\\s+)?(the\\s+)?system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("developer\\s+message", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bypass\\s+(the\\s+)?(approval|security|safety)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("do\\s+not\\s+follow\\s+(the\\s+)?rules", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE)
    );

    public GuardrailResult validate(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.block("Customer message cannot be empty.");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            return GuardrailResult.block("Customer message exceeds the maximum allowed length.");
        }

        String normalized = input.replace('\u0000', ' ').trim();
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return GuardrailResult.block("Request blocked by the prompt-injection guardrail.");
            }
        }
        return GuardrailResult.allow();
    }
}
