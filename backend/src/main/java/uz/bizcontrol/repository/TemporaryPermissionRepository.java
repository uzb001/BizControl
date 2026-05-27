package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.bizcontrol.entity.TemporaryPermission;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TemporaryPermissionRepository extends JpaRepository<TemporaryPermission, Long> {

    List<TemporaryPermission> findByCompanyIdOrderByGrantedAtDesc(Long companyId);

    @Query("SELECT t FROM TemporaryPermission t WHERE t.userId = :uid " +
           "AND t.companyId = :cid AND t.active = true AND t.expiresAt > :now AND t.revokedAt IS NULL")
    List<TemporaryPermission> findEffective(
            @Param("uid") Long userId,
            @Param("cid") Long companyId,
            @Param("now") LocalDateTime now);

    @Query("SELECT t.permissionCode FROM TemporaryPermission t WHERE t.userId = :uid " +
           "AND t.companyId = :cid AND t.active = true AND t.expiresAt > :now AND t.revokedAt IS NULL")
    List<String> findEffectiveCodes(
            @Param("uid") Long userId,
            @Param("cid") Long companyId,
            @Param("now") LocalDateTime now);
}
