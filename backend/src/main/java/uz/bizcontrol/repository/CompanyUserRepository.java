package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.bizcontrol.entity.CompanyUser;
import java.util.List;
import java.util.Optional;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {
    Optional<CompanyUser> findByCompanyIdAndUserId(Long companyId, Long userId);
    List<CompanyUser> findByUserId(Long userId);
    List<CompanyUser> findByCompanyId(Long companyId);
    boolean existsByCompanyIdAndUserId(Long companyId, Long userId);

    /** Count active OWNER members in a company (checks both roleObj.code and legacy role string). */
    @Query("SELECT COUNT(cu) FROM CompanyUser cu WHERE cu.company.id = :companyId " +
           "AND cu.status = 'active' " +
           "AND (cu.roleObj.code = 'OWNER' OR (cu.roleObj IS NULL AND cu.role = 'OWNER'))")
    long countActiveOwners(@Param("companyId") Long companyId);

    /** Lightweight per-request membership status check (used by JwtAuthenticationFilter). */
    @Query("SELECT cu.status FROM CompanyUser cu WHERE cu.company.id = :companyId AND cu.user.id = :userId")
    Optional<String> findStatusByCompanyIdAndUserId(@Param("companyId") Long companyId,
                                                    @Param("userId")    Long userId);
}
