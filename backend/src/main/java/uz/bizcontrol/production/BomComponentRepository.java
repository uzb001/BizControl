package uz.bizcontrol.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface BomComponentRepository extends JpaRepository<BomComponent, Long> {
    List<BomComponent> findByBomTemplateId(Long bomTemplateId);
    @Transactional
    void deleteByBomTemplateId(Long bomTemplateId);
}
