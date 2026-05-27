package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.Permission;
import java.util.List;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findAllByOrderByGroupNameAscCodeAsc();

    @Query("SELECT p.code FROM Permission p JOIN p.id i " +
           "WHERE i IN (SELECT rp.id FROM Role r JOIN r.permissions rp WHERE r.id = :roleId)")
    Set<String> findCodesByRoleId(Long roleId);

    // Use a simpler JPQL approach:
    @Query(value = """
        SELECT p.code FROM permissions p
        INNER JOIN role_permissions rp ON rp.permission_id = p.id
        WHERE rp.role_id = :roleId
        """, nativeQuery = true)
    Set<String> findPermissionCodesByRoleId(Long roleId);
}
