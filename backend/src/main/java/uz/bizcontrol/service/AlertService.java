package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.bizcontrol.entity.Alert;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.AlertRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final CompanyService companyService;

    public void create(Long companyId, String alertType, String title, String message, String entityType, Long entityId) {
        Company company = companyService.getById(companyId);
        Alert alert = Alert.builder()
                .company(company)
                .alertType(alertType)
                .title(title)
                .message(message)
                .relatedEntityType(entityType)
                .relatedEntityId(entityId)
                .status("new")
                .build();
        alertRepository.save(alert);
    }

    public List<Alert> getNew(Long companyId) {
        return alertRepository.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, "new");
    }

    public Alert markSeen(Long companyId, Long id) {
        Alert alert = alertRepository.findById(id)
                .filter(a -> a.getCompany().getId().equals(companyId))
                .orElseThrow(() -> BusinessException.notFound("Alert"));
        alert.setStatus("seen");
        return alertRepository.save(alert);
    }

    public Alert markResolved(Long companyId, Long id) {
        Alert alert = alertRepository.findById(id)
                .filter(a -> a.getCompany().getId().equals(companyId))
                .orElseThrow(() -> BusinessException.notFound("Alert"));
        alert.setStatus("resolved");
        return alertRepository.save(alert);
    }

    public long countNew(Long companyId) {
        return alertRepository.countByCompanyIdAndStatus(companyId, "new");
    }
}
