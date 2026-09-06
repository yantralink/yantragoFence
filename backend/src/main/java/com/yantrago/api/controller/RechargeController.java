package com.yantrago.api.controller;

import com.yantrago.api.model.Recharge;
import com.yantrago.api.service.RechargeService;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Recharge endpoints — list, get, create recharges.
 *
 * GET  /api/v1/recharges — list recharges (paged, filtered by tenant)
 * GET  /api/v1/recharges?deviceId={uuid} — filter by device
 * GET  /api/v1/recharges/{id} — get recharge details
 * POST /api/v1/recharges — create a recharge record
 */
@RestController
@RequestMapping("/api/v1/recharges")
@Validated
public class RechargeController {

    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @GetMapping
    public ResponseEntity<Page<Recharge>> listRecharges(
            @RequestParam(required = false) UUID deviceId,
            Pageable pageable) {
        if (deviceId != null) {
            return ResponseEntity.ok(rechargeService.listRechargesByDevice(deviceId, pageable));
        }
        return ResponseEntity.ok(rechargeService.listRecharges(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Recharge> getRecharge(@PathVariable UUID id) {
        return ResponseEntity.ok(rechargeService.getRecharge(id));
    }

    @PostMapping
    public ResponseEntity<Recharge> createRecharge(@RequestBody Map<String, Object> body) {
        UUID deviceId = UUID.fromString((String) body.get("deviceId"));
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String currency = (String) body.getOrDefault("currency", "INR");
        String provider = (String) body.get("provider");
        String planName = (String) body.get("planName");
        LocalDateTime validUntil = body.get("validUntil") != null
                ? LocalDateTime.parse((String) body.get("validUntil"))
                : null;

        return ResponseEntity.ok(rechargeService.createRecharge(deviceId, amount, currency, provider, planName, validUntil));
    }
}
