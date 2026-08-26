package com.rj1399.customersupport.controller;

import com.rj1399.customersupport.api.ApiDtos;
import com.rj1399.customersupport.service.CustomerSupportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CustomerSupportController {
    private final CustomerSupportService service;

    public CustomerSupportController(CustomerSupportService service) {
        this.service = service;
    }

    @GetMapping("/customers/{id}")
    public ApiDtos.CustomerResponse getCustomer(@PathVariable UUID id) {
        return service.getCustomer(id);
    }

    @GetMapping("/orders/{orderNumber}")
    public ApiDtos.OrderResponse getOrder(@PathVariable String orderNumber) {
        return service.getOrder(orderNumber);
    }

    @GetMapping("/orders/{orderNumber}/delivery")
    public ApiDtos.DeliveryResponse getDelivery(@PathVariable String orderNumber) {
        return service.getDelivery(orderNumber);
    }

    @GetMapping("/orders/{orderNumber}/payment")
    public ApiDtos.PaymentResponse getPayment(@PathVariable String orderNumber) {
        return service.getPayment(orderNumber);
    }

    @GetMapping("/refund-policy/{orderNumber}")
    public ApiDtos.RefundPolicyResponse checkRefundPolicy(@PathVariable String orderNumber) {
        return service.checkRefundPolicy(orderNumber);
    }

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.RefundResponse createRefund(@Valid @RequestBody ApiDtos.RefundRequest request) {
        return service.createRefund(request);
    }

    @GetMapping("/tickets/{ticketNumber}")
    public ApiDtos.TicketResponse getTicket(@PathVariable String ticketNumber) {
        return service.getTicket(ticketNumber);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.TicketResponse createTicket(@Valid @RequestBody ApiDtos.TicketRequest request) {
        return service.createTicket(request);
    }
}
