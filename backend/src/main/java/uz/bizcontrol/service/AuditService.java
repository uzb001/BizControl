package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.bizcontrol.entity.AuditLog;
import uz.bizcontrol.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Long companyId, Long userId, String actionType, String entityType, Long entityId, String oldValue, String newValue) {
        AuditLog log = AuditLog.builder()
                .companyId(companyId)
                .userId(userId)
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        auditLogRepository.save(log);
    }
}
