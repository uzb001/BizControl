package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Role;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Role> findByCompanyIdAndCode(Long companyId, String code);

    Optional<Role> findByCompanyIdAndId(Long companyId, Long id);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    @Query("SELECT COUNT(cu) FROM CompanyUser cu WHERE cu.roleObj.id = :roleId AND cu.status = 'active'")
    long countActiveUsersByRole(Long roleId);
}
