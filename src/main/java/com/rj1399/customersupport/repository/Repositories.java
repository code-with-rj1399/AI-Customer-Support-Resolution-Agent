package com.rj1399.customersupport.repository;

import com.rj1399.customersupport.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
}

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(String orderNumber);
}

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
}

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
    boolean existsByOrderIdAndStatus(UUID orderId, Refund.Status status);
}

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);
}
