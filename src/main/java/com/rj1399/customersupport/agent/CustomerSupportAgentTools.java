package com.rj1399.customersupport.agent;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic business capabilities exposed to the supervisor agent.
 *
 * The LLM can select these tools, but it never gets direct access to the
 * repositories or database. Business invariants remain inside the service layer.
 */
@Component
public class CustomerSupportAgentTools {

    private final CustomerSupportService service;

    public CustomerSupportAgentTools(CustomerSupportService service) {
        this.service = service;
    }

    @Tool(description = "Look up a customer by customer UUID. Use this when customer identity or contact details are needed.")
    public ApiDtos.CustomerResponse getCustomer(String customerId) {
        return service.getCustomer(UUID.fromString(customerId));
    }

    @Tool(description = "Look up an order by its order number. Returns order status, amount, customer ID and delivery dates.")
    public ApiDtos.OrderResponse getOrder(String orderNumber) {
        return service.getOrder(orderNumber);
    }

    @Tool(description = "Check the delivery status of an order and calculate how many days late it is.")
    public ApiDtos.DeliveryResponse getDeliveryStatus(String orderNumber) {
        return service.getDelivery(orderNumber);
    }

    @Tool(description = "Look up the payment status and amount for an order. Use this before deciding whether a refund can be issued.")
    public ApiDtos.PaymentResponse getPayment(String orderNumber) {
        return service.getPayment(orderNumber);
    }

    @Tool(description = "Evaluate the deterministic refund policy for an order. This is the authoritative source for refund eligibility.")
    public ApiDtos.RefundPolicyResponse checkRefundPolicy(String orderNumber) {
        return service.checkRefundPolicy(orderNumber);
    }

    @Tool(description = "Create a refund for an eligible order. Requires a unique idempotency key. The backend enforces refund eligibility, payment state, refund limits and duplicate protection.")
    public ApiDtos.RefundResponse createRefund(String orderNumber, String reason, String idempotencyKey) {
        return service.createRefund(new ApiDtos.RefundRequest(orderNumber, reason, idempotencyKey));
    }

    @Tool(description = "Look up an existing support ticket by ticket number.")
    public ApiDtos.TicketResponse getSupportTicket(String ticketNumber) {
        return service.getTicket(ticketNumber);
    }
}
