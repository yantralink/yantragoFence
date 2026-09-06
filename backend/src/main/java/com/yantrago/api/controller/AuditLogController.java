package com.yantrago.api.controller;

import com.yantrago.api.model.AuditLog;
import com.yantrago.api.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Audit log endpoints — query audit logs.
 *
 * GET /api/v1/audit-logs — list audit logs (paged, filtered by tenant)
 * GET /api/v1/audit-logs?userId={uuid} — filter by user
 *
 * Audit logs are written automatically by AuditLogInterceptor (Phase 5).
 * This controller is read-only.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> listAuditLogs(
            @RequestParam(required = false) UUID userId,
            Pageable pageable) {
        if (userId != null) {
            return ResponseEntity.ok(auditLogService.listAuditLogsByUser(userId, pageable));
        }
        return ResponseEntity.ok(auditLogService.listAuditLogs(pageable));
    }
}
