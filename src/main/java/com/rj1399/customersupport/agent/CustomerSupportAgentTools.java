package com.rj1399.customersupport.agent;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.hitl.HumanApprovalService;
import com.rj1399.customersupport.rag.PolicyKnowledgeService;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic business capabilities exposed to the agents.
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
    private final PolicyKnowledgeService knowledgeService;
    private final HumanApprovalService humanApprovalService;

    public CustomerSupportAgentTools(CustomerSupportService service,
                                     AgentTraceStore traceStore,
                                     PolicyKnowledgeService knowledgeService,
                                     HumanApprovalService humanApprovalService) {
        this.service = service;
        this.traceStore = traceStore;
        this.knowledgeService = knowledgeService;
        this.humanApprovalService = humanApprovalService;
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

    @Tool(description = "Search the customer support policy knowledge base for relevant policy passages. Use this when policy context or an explanation is needed. The retrieved documents are informational; deterministic backend rules remain authoritative for state changes.")
    public PolicyKnowledgeService.KnowledgeSearchResult searchKnowledgeBase(String query) {
        String executionId = CURRENT_EXECUTION.get();
        long started = System.nanoTime();
        if (executionId != null) {
            traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.KNOWLEDGE_SEARCH,
                    "rag", "policy-knowledge-search", 0, Map.of("queryLength", query.length())));
        }
        try {
            var result = knowledgeService.search(query);
            long duration = (System.nanoTime() - started) / 1_000_000;
            if (executionId != null) {
                traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.KNOWLEDGE_RESPONSE,
                        "rag", "policy-knowledge-search", duration,
                        Map.of("matches", result.matches().size(), "sources", result.matches().stream().map(PolicyKnowledgeService.KnowledgeMatch::source).toList())));
            }
            return result;
        } catch (RuntimeException ex) {
            long duration = (System.nanoTime() - started) / 1_000_000;
            if (executionId != null) {
                traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.TOOL_ERROR,
                        "rag", "policy-knowledge-search", duration,
                        Map.of("errorType", ex.getClass().getSimpleName(), "message", safe(ex.getMessage()))));
            }
            throw ex;
        }
    }

    @Tool(description = "Request a refund. The backend first validates the refund policy and payment state. Refunds above the configured human-approval threshold create a pending human approval instead of executing the refund. Never claim the refund was completed when this tool returns a pending approval.")
    public RefundActionResult requestRefund(String orderNumber, String reason, String idempotencyKey) {
        String executionId = CURRENT_EXECUTION.get();
        ApiDtos.RefundPolicyResponse policy = service.checkRefundPolicy(orderNumber);
        if (!policy.eligible()) {
            return new RefundActionResult("REJECTED", orderNumber, policy.rule(), null, null);
        }

        ApiDtos.PaymentResponse payment = service.getPayment(orderNumber);
        if (!"CAPTURED".equalsIgnoreCase(payment.status())) {
            return new RefundActionResult("REJECTED", orderNumber, "Refund requires a captured payment.", null, null);
        }

        ApiDtos.OrderResponse order = service.getOrder(orderNumber);
        java.math.BigDecimal refundAmount = order.totalAmount();

        if (humanApprovalService.requiresApproval(refundAmount)) {
            HumanApprovalService.Approval approval = humanApprovalService.create(
                    orderNumber,
                    refundAmount,
                    reason,
                    idempotencyKey,
                    executionId);
            if (executionId != null) {
                traceStore.add(new AgentTrace(executionId, Instant.now(), AgentTrace.TraceEventType.HUMAN_APPROVAL_REQUESTED,
                        "hitl", "refund-approval", 0,
                        Map.of("approvalId", approval.id().toString(), "orderNumber", orderNumber, "amount", refundAmount)));
            }
            log.info("agent.hitl.requested executionId={} approvalId={} orderNumber={} amount={}", executionId, approval.id(), orderNumber, refundAmount);
            return new RefundActionResult("PENDING_HUMAN_APPROVAL", orderNumber,
                    "Refund is eligible but requires human approval because the amount exceeds the automatic approval threshold.",
                    approval.id(), refundAmount);
        }

        ApiDtos.RefundResponse refund = createRefund(orderNumber, reason, idempotencyKey);
        return new RefundActionResult("COMPLETED", orderNumber,
                "Refund created successfully.", null, refund.amount());
    }

    @Tool(description = "Create a refund only after the refund request has passed the backend approval flow. Do not call this directly for a new customer refund request; use requestRefund instead.")
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

    public record RefundActionResult(String status, String orderNumber, String message, UUID approvalId, java.math.BigDecimal amount) {}

    @FunctionalInterface
    private interface ToolCall<T> { T call(); }
}
