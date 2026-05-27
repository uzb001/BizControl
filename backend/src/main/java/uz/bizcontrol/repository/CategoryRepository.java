package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.bizcontrol.entity.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByCompanyIdAndStatus(Long companyId, String status);
    List<Category> findByCompanyId(Long companyId);
    Optional<Category> findByCompanyIdAndId(Long companyId, Long id);
}
