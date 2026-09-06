package com.yantrago.api.repository;

import com.yantrago.api.model.AuditLog;
import com.yantrago.api.model.AuditLogPK;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLogPK> {
}
