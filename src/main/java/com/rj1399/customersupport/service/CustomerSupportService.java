package com.rj1399.customersupport.service;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.domain.*;
import com.rj1399.customersupport.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CustomerSupportService {
    public static final int REFUND_MINIMUM_DELAY_DAYS = 3;
    public static final BigDecimal MAX_AUTOMATIC_REFUND = new BigDecimal("5000.00");
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final SupportTicketRepository ticketRepository;
    private final Clock clock;
    public CustomerSupportService(CustomerRepository customerRepository, OrderRepository orderRepository, PaymentRepository paymentRepository, RefundRepository refundRepository, SupportTicketRepository ticketRepository, Clock clock) { this.customerRepository=customerRepository; this.orderRepository=orderRepository; this.paymentRepository=paymentRepository; this.refundRepository=refundRepository; this.ticketRepository=ticketRepository; this.clock=clock; }
    @Transactional(readOnly=true) public ApiDtos.CustomerResponse getCustomer(UUID id){ Customer c=customerRepository.findById(id).orElseThrow(()->notFound("Customer",id.toString())); return new ApiDtos.CustomerResponse(c.getId(),c.getName(),c.getEmail(),c.getCreatedAt()); }
    @Transactional(readOnly=true) public ApiDtos.OrderResponse getOrder(String orderNumber){ return toOrderResponse(findOrder(orderNumber)); }
    @Transactional(readOnly=true) public ApiDtos.DeliveryResponse getDelivery(String orderNumber){ Order o=findOrder(orderNumber); return new ApiDtos.DeliveryResponse(o.getOrderNumber(),o.getStatus().name(),o.getExpectedDeliveryDate(),o.getDeliveredDate(),daysLate(o)); }
    @Transactional(readOnly=true) public ApiDtos.PaymentResponse getPayment(String orderNumber){ Order o=findOrder(orderNumber); Payment p=paymentRepository.findByOrderId(o.getId()).orElseThrow(()->notFound("Payment for order",orderNumber)); return new ApiDtos.PaymentResponse(p.getId(),p.getPaymentReference(),orderNumber,p.getAmount(),p.getStatus().name()); }
    @Transactional(readOnly=true) public ApiDtos.RefundPolicyResponse checkRefundPolicy(String orderNumber){ Order o=findOrder(orderNumber); Payment p=paymentRepository.findByOrderId(o.getId()).orElseThrow(()->notFound("Payment for order",orderNumber)); long delay=daysLate(o); boolean eligible=delay>=REFUND_MINIMUM_DELAY_DAYS&&p.getStatus()==Payment.Status.CAPTURED&&!refundRepository.existsByOrderIdAndStatus(o.getId(),Refund.Status.COMPLETED); String rule=eligible?"Eligible: delivery delay meets policy and payment is captured.":"Not eligible: delay, payment status, or existing refund does not satisfy policy."; return new ApiDtos.RefundPolicyResponse(eligible,REFUND_MINIMUM_DELAY_DAYS,MAX_AUTOMATIC_REFUND,rule); }
    @Transactional public ApiDtos.RefundResponse createRefund(ApiDtos.RefundRequest request){ return createRefundInternal(request,false); }
    @Transactional public ApiDtos.RefundResponse createApprovedRefund(ApiDtos.RefundRequest request){ return createRefundInternal(request,true); }
    private ApiDtos.RefundResponse createRefundInternal(ApiDtos.RefundRequest request, boolean humanApproved){ Refund existing=refundRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null); if(existing!=null)return toRefundResponse(existing); Order order=findOrder(request.orderNumber()); Payment payment=paymentRepository.findByOrderId(order.getId()).orElseThrow(()->notFound("Payment for order",request.orderNumber())); long delay=daysLate(order); if(delay<REFUND_MINIMUM_DELAY_DAYS)throw new BusinessRuleException("REFUND_NOT_ELIGIBLE","Order "+order.getOrderNumber()+" is only "+delay+" day(s) late; policy requires "+REFUND_MINIMUM_DELAY_DAYS+" days."); if(payment.getStatus()!=Payment.Status.CAPTURED)throw new BusinessRuleException("REFUND_NOT_ELIGIBLE","Payment is not in CAPTURED state."); if(!humanApproved&&order.getTotalAmount().compareTo(MAX_AUTOMATIC_REFUND)>0)throw new BusinessRuleException("HUMAN_APPROVAL_REQUIRED","Refund exceeds the automatic refund limit of "+MAX_AUTOMATIC_REFUND+"."); if(refundRepository.existsByOrderIdAndStatus(order.getId(),Refund.Status.COMPLETED))throw new BusinessRuleException("REFUND_ALREADY_COMPLETED","A completed refund already exists for this order."); Refund refund=new Refund("RF-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),request.idempotencyKey(),order,order.getTotalAmount(),request.reason(),Refund.Status.COMPLETED); return toRefundResponse(refundRepository.save(refund)); }
    @Transactional public ApiDtos.TicketResponse createTicket(ApiDtos.TicketRequest request){ Customer c=customerRepository.findById(request.customerId()).orElseThrow(()->notFound("Customer",request.customerId().toString())); Order o=request.orderId()==null?null:orderRepository.findById(request.orderId()).orElseThrow(()->notFound("Order",request.orderId().toString())); SupportTicket t=new SupportTicket("TKT-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),c,o,request.subject(),request.description(),SupportTicket.Status.OPEN); return toTicketResponse(ticketRepository.save(t)); }
    @Transactional(readOnly=true) public ApiDtos.TicketResponse getTicket(String ticketNumber){ SupportTicket t=ticketRepository.findByTicketNumber(ticketNumber).orElseThrow(()->notFound("Ticket",ticketNumber)); return toTicketResponse(t); }
    private Order findOrder(String n){return orderRepository.findByOrderNumber(n).orElseThrow(()->notFound("Order",n));} private long daysLate(Order o){LocalDate end=o.getDeliveredDate()!=null?o.getDeliveredDate():LocalDate.now(clock);return Math.max(0,ChronoUnit.DAYS.between(o.getExpectedDeliveryDate(),end));} private ApiDtos.OrderResponse toOrderResponse(Order o){return new ApiDtos.OrderResponse(o.getId(),o.getOrderNumber(),o.getCustomer().getId(),o.getTotalAmount(),o.getStatus().name(),o.getExpectedDeliveryDate(),o.getDeliveredDate(),daysLate(o));} private ApiDtos.RefundResponse toRefundResponse(Refund r){return new ApiDtos.RefundResponse(r.getId(),r.getRefundReference(),r.getOrder().getOrderNumber(),r.getAmount(),r.getReason(),r.getStatus().name(),r.getCreatedAt());} private ApiDtos.TicketResponse toTicketResponse(SupportTicket t){return new ApiDtos.TicketResponse(t.getId(),t.getTicketNumber(),t.getCustomer().getId(),t.getOrder()==null?null:t.getOrder().getOrderNumber(),t.getSubject(),t.getDescription(),t.getStatus().name(),t.getCreatedAt());} private RuntimeException notFound(String r,String id){return new ResourceNotFoundException(r+" not found: "+id);} public static class ResourceNotFoundException extends RuntimeException{public ResourceNotFoundException(String m){super(m);}} public static class BusinessRuleException extends RuntimeException{private final String code;public BusinessRuleException(String code,String m){super(m);this.code=code;}public String getCode(){return code;}}
}
