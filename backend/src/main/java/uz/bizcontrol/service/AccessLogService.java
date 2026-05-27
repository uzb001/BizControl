package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uz.bizcontrol.entity.AccessLog;
import uz.bizcontrol.repository.AccessLogRepository;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository repo;

    @Async
    public void logDenied(Long companyId, Long userId, String action, String module, String reason) {
        logDenied(companyId, userId, action, module, reason, null);
    }

    @Async
    public void logDenied(Long companyId, Long userId, String action, String module, String reason, String ipAddress) {
        repo.save(AccessLog.builder()
                .companyId(companyId)
                .userId(userId)
                .action(action)
                .module(module)
                .result("DENIED")
                .reason(reason)
                .ipAddress(ipAddress)
                .build());
    }

    @Async
    public void logAllowed(Long companyId, Long userId, String action, String module) {
        repo.save(AccessLog.builder()
                .companyId(companyId)
                .userId(userId)
                .action(action)
                .module(module)
                .result("ALLOWED")
                .build());
    }
}
