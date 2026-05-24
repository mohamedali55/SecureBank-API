package com.securebank.service;

import com.securebank.domain.AuditLog;
import com.securebank.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit-trail rows. {@link #record} joins the caller's transaction so that, for an
 * atomic transfer, the audit row commits or rolls back together with the money movement.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String username, String action, String detail) {
        auditLogRepository.save(new AuditLog(username, action, detail, currentIpAddress()));
    }

    private String currentIpAddress() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getRemoteAddr() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
