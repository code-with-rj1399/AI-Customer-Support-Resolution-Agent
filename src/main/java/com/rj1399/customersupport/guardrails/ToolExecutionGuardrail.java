package com.rj1399.customersupport.guardrails;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ToolExecutionGuardrail {
    private static final Set<String> HIGH_RISK_TOOLS = Set.of("createRefund", "requestRefund");

    public GuardrailResult validate(String toolName, String orderNumber, String reason) {
        if (toolName == null || toolName.isBlank()) {
            return GuardrailResult.block("Tool name is required.");
        }
        if (HIGH_RISK_TOOLS.contains(toolName)) {
            if (orderNumber == null || orderNumber.isBlank()) {
                return GuardrailResult.block("Order number is required for a refund operation.");
            }
            if (reason == null || reason.isBlank()) {
                return GuardrailResult.block("Refund reason is required.");
            }
        }
        return GuardrailResult.allow();
    }

    public boolean isHighRisk(String toolName) {
        return HIGH_RISK_TOOLS.contains(toolName);
    }
}
