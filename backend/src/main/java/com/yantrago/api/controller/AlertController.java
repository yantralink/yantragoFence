package com.yantrago.api.controller;

import com.yantrago.api.dto.alert.AlertDto;
import com.yantrago.api.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Alert endpoints — list, get, acknowledge alerts.
 *
 * GET /api/v1/alerts — list alerts (paged, filtered by tenant)
 * GET /api/v1/alerts/unacknowledged — list unacknowledged alerts
 * GET /api/v1/alerts?machineId={uuid} — filter by machine
 * GET /api/v1/alerts/{id} — get alert details
 * POST /api/v1/alerts/{id}/acknowledge — acknowledge an alert
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<Page<AlertDto>> listAlerts(
            @RequestParam(required = false) UUID machineId,
            @RequestParam(required = false) Boolean unacknowledged,
            Pageable pageable) {
        if (Boolean.TRUE.equals(unacknowledged)) {
            return ResponseEntity.ok(alertService.listUnacknowledgedAlerts(pageable));
        }
        if (machineId != null) {
            return ResponseEntity.ok(alertService.listAlertsByMachine(machineId, pageable));
        }
        return ResponseEntity.ok(alertService.listAlerts(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDto> getAlert(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.getAlert(id));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<AlertDto> acknowledgeAlert(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.acknowledgeAlert(id));
    }
}
