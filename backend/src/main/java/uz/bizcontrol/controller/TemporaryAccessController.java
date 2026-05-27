package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.TemporaryPermission;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.TemporaryAccessService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for managing temporary permissions.
 *
 * POST   /temp-access/grant          — grant temp permission to a user
 * DELETE /temp-access/{id}/revoke    — revoke a temp permission
 * GET    /temp-access                — list all temp permissions for company
 * GET    /temp-access/user/{userId}  — list effective temp permissions for one user
 */
@RestController
@RequestMapping("/temp-access")
@RequiredArgsConstructor
public class TemporaryAccessController {

    private final TemporaryAccessService tempAccessService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<TemporaryPermission>> list(
            @AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "users.change_role");
        return ResponseEntity.ok(tempAccessService.listForCompany(principal.getCompanyId()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TemporaryPermission>> listForUser(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long userId) {
        permissionService.require(principal, "users.change_role");
        return ResponseEntity.ok(tempAccessService.listEffectiveForUser(userId, principal.getCompanyId()));
    }

    @PostMapping("/grant")
    public ResponseEntity<TemporaryPermission> grant(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Map<String, Object> body) {
        permissionService.require(principal, "users.change_role");

        Long targetUserId = Long.parseLong(body.get("userId").toString());
        String permCode = body.get("permissionCode").toString();
        LocalDateTime expiresAt = LocalDateTime.parse(body.get("expiresAt").toString());
        String reason = body.getOrDefault("reason", "").toString();

        return ResponseEntity.ok(
                tempAccessService.grant(principal, targetUserId, permCode, expiresAt, reason));
    }

    @DeleteMapping("/{id}/revoke")
    public ResponseEntity<TemporaryPermission> revoke(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "users.change_role");
        return ResponseEntity.ok(tempAccessService.revoke(principal, id));
    }
}
