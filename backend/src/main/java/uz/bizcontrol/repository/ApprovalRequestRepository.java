package uz.bizcontrol.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.bizcontrol.entity.ApprovalRequest;

import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Page<ApprovalRequest> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    Page<ApprovalRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(
            Long companyId, String status, Pageable pageable);

    List<ApprovalRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);

    @Query("SELECT COUNT(a) FROM ApprovalRequest a WHERE a.companyId = :cid AND a.status = 'pending'")
    long countPending(@Param("cid") Long companyId);

    @Query("SELECT a FROM ApprovalRequest a WHERE a.companyId = :cid " +
           "AND a.entityType = :entityType AND a.entityId = :entityId AND a.status = 'pending'")
    List<ApprovalRequest> findPendingByEntity(
            @Param("cid") Long companyId,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);
}
