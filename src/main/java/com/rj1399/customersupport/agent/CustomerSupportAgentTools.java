package com.rj1399.customersupport.agent;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic business capabilities exposed to the supervisor agent.
 *
 * The LLM can select these tools, but it never gets direct access to the
 * repositories or database. Business invariants remain inside the service layer.
 */
@Component
public class CustomerSupportAgentTools {
    private static final Logger log = LoggerFactory.getLogger(CustomerSupportAgentTools.class);
    private static final ThreadLocal<String> CURRENT_EXECUTION = new ThreadLocal<>();

    private final CustomerSupportService service;
    private final AgentTraceStore traceStore;

    public CustomerSupportAgentTools(CustomerSupportService service, AgentTraceStore traceStore) {
        this.service = service;
        this.traceStore = traceStore;
    }

    public static void bindExecution(String executionId) { CURRENT_EXECUTION.set(executionId); }
    public static void clearExecution() { CURRENT_EXECUTION.remove(); }

    @Tool(description = "Look up a customer by customer UUID. Use this when customer identity or contact details are needed.")
    public ApiDtos.CustomerResponse getCustomer(String customerId) {
        return execute("getCustomer", Map.of("customerId", customerId), () -> service.getCustomer(UUID.fromString(customerId)));
    }

    @Tool(description = "Look up an order by its order number. Returns order status, amount, customer ID and delivery dates.")
    public ApiDtos.OrderResponse getOrder(String orderNumber) {
        return execute("getOrder", Map.of("orderNumber", orderNumber), () -> service.getOrder(orderNumber));
    }

    @Tool(description = "Check the delivery status of an order and calculate how many days late it is.")
    public ApiDtos.DeliveryResponse getDeliveryStatus(String orderNumber) {
        return execute("getDeliveryStatus", Map.of("orderNumber", orderNumber), () -> service.getDelivery(orderNumber));
    }

    @Tool(description = "Look up the payment status and amount for an order. Use this before deciding whether a refund can be issued.")
    public ApiDtos.PaymentResponse getPayment(String orderNumber) {
        return execute("getPayment", Map.of("orderNumber", orderNumber), () -> service.getPayment(orderNumber));
    }

    @Tool(description = "Evaluate the deterministic refund policy for an order. This is the authoritative source for refund eligibility.")
    public ApiDtos.RefundPolicyResponse checkRefundPolicy(String orderNumber) {
        return execute("checkRefundPolicy", Map.of("orderNumber", orderNumber), () -> service.checkRefundPolicy(orderNumber));
    }

    @Tool(description = "Create a refund for an eligible order. Requires a unique idempotency key. The backend enforces refund eligibility, payment state, refund limits and duplicate protection.")
    public ApiDtos.RefundResponse createRefund(String orderNumber, String reason, String idempotencyKey) {
        return execute("createRefund", Map.of("orderNumber", orderNumber, "reason", reason),
                () -> service.createRefund(new ApiDtos.RefundRequest(orderNumber, reason, idempotencyKey)));
    }

    @Tool(description = "Look up an existing support ticket by ticket number.")
    public ApiDtos.TicketResponse getSupportTicket(String ticketNumber) {
        return execute("getSupportTicket", Map.of("ticketNumber", ticketNumber), () -> service.getTicket(ticketNumber));
    }

    private <T> T execute(String toolName, Map<String, Object> input, ToolCall<T> operation) {
        String executionId = CURRENT_EXECUTION.get();
        long started = System.nanoTime();
        if (executionId != null) {
            traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.TOOL_REQUEST,
                    "tool", toolName, 0, input));
        }
        log.info("agent.tool.request executionId={} tool={} input={}", executionId, toolName, input);
        try {
            T result = operation.call();
            long duration = (System.nanoTime() - started) / 1_000_000;
            if (executionId != null) {
                traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.TOOL_RESPONSE,
                        "tool", toolName, duration, Map.of("status", "SUCCESS")));
            }
            log.info("agent.tool.completed executionId={} tool={} durationMs={}", executionId, toolName, duration);
            return result;
        } catch (RuntimeException ex) {
            long duration = (System.nanoTime() - started) / 1_000_000;
            if (executionId != null) {
                traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.TOOL_ERROR,
                        "tool", toolName, duration,
                        Map.of("errorType", ex.getClass().getSimpleName(), "message", safe(ex.getMessage()))));
            }
            log.error("agent.tool.failed executionId={} tool={} durationMs={} errorType={}", executionId, toolName, duration, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    @FunctionalInterface
    private interface ToolCall<T> { T call(); }
}
