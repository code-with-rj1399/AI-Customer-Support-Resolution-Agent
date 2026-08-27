package com.rj1399.customersupport.agent;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.hitl.HumanApprovalService;
import com.rj1399.customersupport.service.CustomerSupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class CustomerSupportAgentTools {
    private static final Logger log=LoggerFactory.getLogger(CustomerSupportAgentTools.class); private static final ThreadLocal<String> CURRENT_EXECUTION=new ThreadLocal<>();
    private final CustomerSupportService service; private final AgentTraceStore traceStore; private final HumanApprovalService humanApprovalService;
    public CustomerSupportAgentTools(CustomerSupportService service,AgentTraceStore traceStore,HumanApprovalService humanApprovalService){this.service=service;this.traceStore=traceStore;this.humanApprovalService=humanApprovalService;}
    public static void bindExecution(String id){CURRENT_EXECUTION.set(id);} public static void clearExecution(){CURRENT_EXECUTION.remove();}
    @Tool(description="Look up a customer by customer UUID.") public ApiDtos.CustomerResponse getCustomer(String customerId){return execute("getCustomer",Map.of("customerId",customerId),()->service.getCustomer(UUID.fromString(customerId)));}
    @Tool(description="Look up an order by order number.") public ApiDtos.OrderResponse getOrder(String orderNumber){return execute("getOrder",Map.of("orderNumber",orderNumber),()->service.getOrder(orderNumber));}
    @Tool(description="Check delivery status and lateness for an order.") public ApiDtos.DeliveryResponse getDeliveryStatus(String orderNumber){return execute("getDeliveryStatus",Map.of("orderNumber",orderNumber),()->service.getDelivery(orderNumber));}
    @Tool(description="Look up payment status and amount for an order.") public ApiDtos.PaymentResponse getPayment(String orderNumber){return execute("getPayment",Map.of("orderNumber",orderNumber),()->service.getPayment(orderNumber));}
    @Tool(description="Evaluate authoritative refund eligibility for an order.") public ApiDtos.RefundPolicyResponse checkRefundPolicy(String orderNumber){return execute("checkRefundPolicy",Map.of("orderNumber",orderNumber),()->service.checkRefundPolicy(orderNumber));}
    @Tool(description="Request a refund. Eligible refunds above the automatic limit create a pending human approval. Never claim completion when status is PENDING_HUMAN_APPROVAL.") public RefundActionResult requestRefund(String orderNumber,String reason,String idempotencyKey){ApiDtos.RefundPolicyResponse policy=service.checkRefundPolicy(orderNumber);if(!policy.eligible())return new RefundActionResult("REJECTED",orderNumber,policy.rule(),null,null);ApiDtos.OrderResponse order=service.getOrder(orderNumber);BigDecimal amount=order.totalAmount();if(humanApprovalService.requiresApproval(amount)){HumanApprovalService.Approval approval=humanApprovalService.create(orderNumber,amount,reason,idempotencyKey,CURRENT_EXECUTION.get());return new RefundActionResult("PENDING_HUMAN_APPROVAL",orderNumber,"Refund is eligible and waiting for human approval.",approval.id(),amount);}ApiDtos.RefundResponse refund=service.createRefund(new ApiDtos.RefundRequest(orderNumber,reason,idempotencyKey));return new RefundActionResult("COMPLETED",orderNumber,"Refund created successfully.",null,refund.amount());}
    @Tool(description="Get the current human approval status and decision details using the approval ID. Use this to check whether a pending refund was approved or rejected.") public HumanApprovalStats getHumanApprovalStats(String approvalId){return execute("getHumanApprovalStats",Map.of("approvalId",approvalId),()->{HumanApprovalService.Approval a=humanApprovalService.get(UUID.fromString(approvalId));return new HumanApprovalStats(a.id(),a.orderNumber(),a.status(),a.amount(),a.createdAt(),a.decidedAt(),a.decisionBy(),a.decisionReason());});}
    @Tool(description="Look up an existing support ticket by ticket number.") public ApiDtos.TicketResponse getSupportTicket(String ticketNumber){return execute("getSupportTicket",Map.of("ticketNumber",ticketNumber),()->service.getTicket(ticketNumber));}
    private <T>T execute(String name,Map<String,Object> input,ToolCall<T> op){String executionId=CURRENT_EXECUTION.get();long started=System.nanoTime();if(executionId!=null)traceStore.add(new AgentTrace(executionId,Instant.now(),AgentTrace.TraceEventType.TOOL_REQUEST,"tool",name,0,input));try{T result=op.call();long duration=(System.nanoTime()-started)/1_000_000;if(executionId!=null)traceStore.add(new AgentTrace(executionId,Instant.now(),AgentTrace.TraceEventType.TOOL_RESPONSE,"tool",name,duration,Map.of("status","SUCCESS")));return result;}catch(RuntimeException ex){long duration=(System.nanoTime()-started)/1_000_000;if(executionId!=null)traceStore.add(new AgentTrace(executionId,Instant.now(),AgentTrace.TraceEventType.TOOL_ERROR,"tool",name,duration,Map.of("errorType",ex.getClass().getSimpleName())));log.error("agent.tool.failed tool={}",name,ex);throw ex;}}
    public record RefundActionResult(String status,String orderNumber,String message,UUID approvalId,BigDecimal amount){} public record HumanApprovalStats(UUID approvalId,String orderNumber,String status,BigDecimal amount,Instant createdAt,Instant decidedAt,String decidedBy,String decisionReason){} @FunctionalInterface private interface ToolCall<T>{T call();}
}
