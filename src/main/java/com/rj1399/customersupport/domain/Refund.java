package com.rj1399.customersupport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(name = "uk_refund_idempotency", columnNames = "idempotency_key"))
public class Refund {
    public enum Status { PENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String refundReference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Refund() {}

    public Refund(String refundReference, String idempotencyKey, Order order, BigDecimal amount,
                  String reason, Status status) {
        this.refundReference = refundReference;
        this.idempotencyKey = idempotencyKey;
        this.order = order;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getRefundReference() { return refundReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Order getOrder() { return order; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
