package com.rj1399.customersupport.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}

    public record CustomerResponse(UUID id, String name, String email, Instant createdAt) {}

    public record OrderResponse(
            UUID id, String orderNumber, UUID customerId, BigDecimal totalAmount,
            String status, LocalDate expectedDeliveryDate, LocalDate deliveredDate,
            long daysLate) {}

    public record DeliveryResponse(
            String orderNumber, String status, LocalDate expectedDeliveryDate,
            LocalDate deliveredDate, long daysLate) {}

    public record PaymentResponse(
            UUID id, String paymentReference, String orderNumber,
            BigDecimal amount, String status) {}

    public record RefundPolicyResponse(
            boolean eligible, int minimumDelayDays, BigDecimal maximumAutomaticRefund,
            String rule) {}

    public record RefundRequest(
            @NotBlank String orderNumber,
            @NotBlank String reason,
            @NotBlank String idempotencyKey) {}

    public record RefundResponse(
            UUID id, String refundReference, String orderNumber,
            BigDecimal amount, String reason, String status, Instant createdAt) {}

    public record TicketRequest(
            @NotNull UUID customerId,
            UUID orderId,
            @NotBlank String subject,
            @NotBlank String description) {}

    public record TicketResponse(
            UUID id, String ticketNumber, UUID customerId, String orderNumber,
            String subject, String description, String status, Instant createdAt) {}

    public record ErrorResponse(String code, String message, Instant timestamp) {}
}
