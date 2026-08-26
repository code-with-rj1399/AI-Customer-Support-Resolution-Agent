package com.rj1399.customersupport.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
    public enum Status { PLACED, PROCESSING, SHIPPED, DELAYED, DELIVERED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(nullable = false)
    private LocalDate expectedDeliveryDate;

    private LocalDate deliveredDate;

    @Column(nullable = false)
    private Instant createdAt;

    protected Order() {}

    public Order(String orderNumber, Customer customer, BigDecimal totalAmount, Status status,
                 LocalDate expectedDeliveryDate, LocalDate deliveredDate) {
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.deliveredDate = deliveredDate;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Customer getCustomer() { return customer; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Status getStatus() { return status; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public LocalDate getDeliveredDate() { return deliveredDate; }
    public Instant getCreatedAt() { return createdAt; }
}
