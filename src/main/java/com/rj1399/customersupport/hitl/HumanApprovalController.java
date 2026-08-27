package com.rj1399.customersupport.hitl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class HumanApprovalController {
    private final HumanApprovalService service;

    public HumanApprovalController(HumanApprovalService service) { this.service = service; }

    @GetMapping("/pending")
    public List<HumanApprovalService.Approval> pending() { return service.pending(); }

    @GetMapping("/{id}")
    public HumanApprovalService.Approval get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/{id}/approve")
    public HumanApprovalService.Approval approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return service.approve(id, request.decidedBy(), request.reason());
    }

    @PostMapping("/{id}/reject")
    public HumanApprovalService.Approval reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return service.reject(id, request.decidedBy(), request.reason());
    }

    public record DecisionRequest(@NotBlank String decidedBy, String reason) {}
}
