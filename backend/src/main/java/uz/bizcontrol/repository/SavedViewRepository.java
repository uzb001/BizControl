package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.SavedView;

import java.util.List;
import java.util.Optional;

public interface SavedViewRepository extends JpaRepository<SavedView, Long> {
    List<SavedView> findByCompanyIdAndModuleOrderByNameAsc(Long companyId, String module);
    List<SavedView> findByCompanyIdAndCreatedByAndModuleOrderByNameAsc(Long companyId, Long userId, String module);
    List<SavedView> findByCompanyIdOrderByModuleAscNameAsc(Long companyId);
    Optional<SavedView> findByCompanyIdAndId(Long companyId, Long id);
}
