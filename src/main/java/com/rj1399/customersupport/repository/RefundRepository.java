package com.rj1399.customersupport.repository;

import com.rj1399.customersupport.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
    boolean existsByOrderIdAndStatus(UUID orderId, Refund.Status status);
}
