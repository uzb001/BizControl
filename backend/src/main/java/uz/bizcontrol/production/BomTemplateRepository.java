package uz.bizcontrol.production;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BomTemplateRepository extends JpaRepository<BomTemplate, Long> {
    List<BomTemplate> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<BomTemplate> findByCompanyIdAndProductIdAndStatus(Long companyId, Long productId, String status);
    Optional<BomTemplate> findByCompanyIdAndId(Long companyId, Long id);
    boolean existsByCompanyIdAndProductIdAndVersion(Long companyId, Long productId, String version);
}
