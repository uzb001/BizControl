package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.TemporaryPermission;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.TemporaryPermissionRepository;
import uz.bizcontrol.security.BizControlPrincipal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemporaryAccessService {

    private final TemporaryPermissionRepository tempPermRepo;
    private final AuditService auditService;
    private final PermissionService permissionService;

    /**
     * Grant a temporary permission to a user until a specific date/time.
     * Grantor must have the permission they are granting (or be OWNER).
     */
    @Transactional
    public TemporaryPermission grant(
            BizControlPrincipal grantor,
            Long targetUserId,
            String permissionCode,
            LocalDateTime expiresAt,
            String reason) {

        // Grantor must themselves have this permission (or be OWNER)
        if (!permissionService.hasPermission(grantor, permissionCode)) {
            throw BusinessException.forbidden(
                    "You cannot grant a permission you don't have: " + permissionCode);
        }

        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException("expiresAt must be a future date/time");
        }

        if (expiresAt.isAfter(LocalDateTime.now().plusDays(30))) {
            throw new BusinessException("Temporary access cannot exceed 30 days");
        }

        TemporaryPermission tp = TemporaryPermission.builder()
                .companyId(grantor.getCompanyId())
                .userId(targetUserId)
                .grantedBy(grantor.getUserId())
                .permissionCode(permissionCode)
                .reason(reason)
                .grantedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .active(true)
                .build();

        tempPermRepo.save(tp);
        log.info("[TempAccess] {} granted '{}' to userId={} until {}",
                grantor.getUserId(), permissionCode, targetUserId, expiresAt);
        auditService.log(grantor.getCompanyId(), grantor.getUserId(),
                "TEMP_ACCESS_GRANTED", "TemporaryPermission", tp.getId(),
                null, "Permission '" + permissionCode + "' to userId=" + targetUserId + " until " + expiresAt);

        // Invalidate permission cache for target user's role
        permissionService.invalidateCache(null); // broad invalidation — cache will reload
        return tp;
    }

    /**
     * Revoke a temporary permission before it expires.
     */
    @Transactional
    public TemporaryPermission revoke(BizControlPrincipal revoker, Long tempPermId) {
        TemporaryPermission tp = tempPermRepo.findById(tempPermId)
                .orElseThrow(() -> BusinessException.notFound("TemporaryPermission"));

        if (!tp.getCompanyId().equals(revoker.getCompanyId()))
            throw BusinessException.forbidden("Not your company");

        if (!tp.isActive())
            throw new BusinessException("Already revoked or expired");

        tp.setActive(false);
        tp.setRevokedAt(LocalDateTime.now());
        tempPermRepo.save(tp);

        auditService.log(revoker.getCompanyId(), revoker.getUserId(),
                "TEMP_ACCESS_REVOKED", "TemporaryPermission", tempPermId,
                "active", "revoked");
        return tp;
    }

    /**
     * Returns the set of currently-effective temporary permission codes for a user.
     * Called from JwtAuthenticationFilter / PermissionService.
     */
    public Set<String> getEffectiveCodes(Long userId, Long companyId) {
        return tempPermRepo.findEffectiveCodes(userId, companyId, LocalDateTime.now())
                .stream().collect(Collectors.toSet());
    }

    public List<TemporaryPermission> listForCompany(Long companyId) {
        return tempPermRepo.findByCompanyIdOrderByGrantedAtDesc(companyId);
    }

    public List<TemporaryPermission> listEffectiveForUser(Long userId, Long companyId) {
        return tempPermRepo.findEffective(userId, companyId, LocalDateTime.now());
    }
}
