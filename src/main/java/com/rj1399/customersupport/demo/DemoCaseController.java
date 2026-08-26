package com.rj1399.customersupport.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/demo-cases")
public class DemoCaseController {
    private final JdbcTemplate jdbc;

    public DemoCaseController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<DemoCase> cases() {
        return jdbc.query("""
                SELECT o.order_number, c.name, o.total_amount, o.status,
                       o.expected_delivery_date, o.delivered_date,
                       COALESCE(p.status, 'NO_PAYMENT') AS payment_status,
                       COALESCE(t.subject, '') AS ticket_subject
                FROM orders o
                JOIN customers c ON c.id = o.customer_id
                LEFT JOIN payments p ON p.order_id = o.id
                LEFT JOIN LATERAL (SELECT subject FROM support_tickets st WHERE st.order_id = o.id ORDER BY created_at DESC LIMIT 1) t ON true
                ORDER BY CAST(o.order_number AS INTEGER)
                """, (rs, row) -> new DemoCase(rs.getString("order_number"), rs.getString("name"), rs.getBigDecimal("total_amount"), rs.getString("status"), rs.getObject("expected_delivery_date", java.time.LocalDate.class), rs.getObject("delivered_date", java.time.LocalDate.class), rs.getString("payment_status"), rs.getString("ticket_subject")));
    }

    public record DemoCase(String orderNumber, String customerName, BigDecimal amount, String status, java.time.LocalDate expectedDeliveryDate, java.time.LocalDate deliveredDate, String paymentStatus, String ticketSubject) {}
}
