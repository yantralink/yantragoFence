package com.yantrago.api.service;

import com.yantrago.api.model.AuditLog;
import com.yantrago.api.repository.AuditLogRepository;
import com.yantrago.api.security.PermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Audit log service — records and queries audit logs.
 *
 * Audit logs are written by AuditLogInterceptor (Phase 5) for every sensitive API call.
 * This service provides query capabilities and a programmatic API for writing audit entries.
 *
 * All queries filter by organization_id from OwnerContextService (tenant isolation).
 * Super_admin can view all audit logs.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final OwnerContextService ownerContextService;
    private final PermissionEvaluator permissionEvaluator;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           JdbcTemplate jdbcTemplate,
                           OwnerContextService ownerContextService,
                           PermissionEvaluator permissionEvaluator) {
        this.auditLogRepository = auditLogRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.ownerContextService = ownerContextService;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * Lists audit logs for the current organization (or all for super_admin).
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> listAuditLogs(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();

        if (orgId == null) {
            // Super_admin: query all audit logs via JdbcTemplate for pagination
            String countSql = "SELECT COUNT(*) FROM audit_logs";
            Long total = jdbcTemplate.queryForObject(countSql, Long.class);
            if (total == null || total == 0) {
                return new PageImpl<>(List.of(), pageable, 0);
            }

            String sql = "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ? OFFSET ?";
            List<AuditLog> logs = jdbcTemplate.query(sql, (rs, rowNum) -> mapAuditLog(rs),
                    pageable.getPageSize(), pageable.getOffset());
            return new PageImpl<>(logs, pageable, total);
        }

        // Tenant user: only their org's audit logs
        String countSql = "SELECT COUNT(*) FROM audit_logs WHERE organization_id = ?";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, orgId);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String sql = "SELECT * FROM audit_logs WHERE organization_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AuditLog> logs = jdbcTemplate.query(sql, (rs, rowNum) -> mapAuditLog(rs),
                orgId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(logs, pageable, total);
    }

    /**
     * Lists audit logs filtered by user.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> listAuditLogsByUser(UUID userId, Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();

        String countSql = "SELECT COUNT(*) FROM audit_logs WHERE organization_id = ? AND user_id = ?";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, orgId, userId);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String sql = "SELECT * FROM audit_logs WHERE organization_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AuditLog> logs = jdbcTemplate.query(sql, (rs, rowNum) -> mapAuditLog(rs),
                orgId, userId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(logs, pageable, total);
    }

    /**
     * Programmatically records an audit log entry.
     * Called by AuditLogInterceptor and other services that need to log actions.
     */
    @Transactional
    public void recordAuditLog(String action, String resourceType, UUID resourceId,
                               String ipAddress, String userAgent, String details) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        UUID userId = permissionEvaluator.getCurrentUserId();

        AuditLog auditLog = new AuditLog();
        auditLog.setOrganizationId(orgId);
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);
        auditLog.setDetails(details);
        auditLog.setCreatedAt(LocalDateTime.now());

        auditLogRepository.save(auditLog);
        log.debug("Recorded audit log: action={} resourceType={} resourceId={} user={}",
                action, resourceType, resourceId, userId);
    }

    private AuditLog mapAuditLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(UUID.fromString(rs.getString("id")));
        String orgId = rs.getString("organization_id");
        auditLog.setOrganizationId(orgId != null ? UUID.fromString(orgId) : null);
        String userId = rs.getString("user_id");
        auditLog.setUserId(userId != null ? UUID.fromString(userId) : null);
        auditLog.setAction(rs.getString("action"));
        auditLog.setResourceType(rs.getString("resource_type"));
        String resId = rs.getString("resource_id");
        auditLog.setResourceId(resId != null ? UUID.fromString(resId) : null);
        auditLog.setIpAddress(rs.getString("ip_address"));
        auditLog.setUserAgent(rs.getString("user_agent"));
        auditLog.setDetails(rs.getString("details"));
        auditLog.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return auditLog;
    }
}
