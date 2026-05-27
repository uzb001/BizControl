package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Role;
import uz.bizcontrol.service.AccessLogService;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.PermissionService;
import uz.bizcontrol.service.RoleService;
import uz.bizcontrol.security.BizControlPrincipal;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolesController {

    private final RoleService       roleService;
    private final PermissionService permissionService;
    private final AuditService      auditService;
    private final AccessLogService  accessLogService;

    /** GET /roles — list all roles for the current company. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "roles.view");
        return ResponseEntity.ok(
                roleService.listForCompany(p.getCompanyId())
                        .stream().map(this::toMap)
                        .collect(Collectors.toList())
        );
    }

    /** GET /roles/{id} — role details + permissions. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id) {
        permissionService.require(p, "roles.view");
        Role role = roleService.getByIdForCompany(p.getCompanyId(), id);
        return ResponseEntity.ok(toMapWithPermissions(role));
    }

    /** POST /roles — create a custom role. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal BizControlPrincipal p,
            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "roles.create");

        String name        = (String) body.get("name");
        String code        = (String) body.get("code");
        String description = (String) body.get("description");
        String color       = (String) body.get("color");

        @SuppressWarnings("unchecked")
        List<Long> permIds = body.containsKey("permissionIds")
                ? ((List<?>) body.get("permissionIds")).stream()
                    .map(v -> Long.parseLong(v.toString()))
                    .collect(Collectors.toList())
                : Collections.emptyList();

        Role role = roleService.create(p.getCompanyId(), name, code, description, color, permIds);
        auditService.log(p.getCompanyId(), p.getUserId(), "CREATE_ROLE",
                "Role", role.getId(), null, "Created role: " + name);
        accessLogService.logAllowed(p.getCompanyId(), p.getUserId(), "roles.create", "roles");
        return ResponseEntity.ok(toMapWithPermissions(role));
    }

    /** PUT /roles/{id} — rename / recolor a role. */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "roles.edit");

        Role role = roleService.update(
                p.getCompanyId(), id,
                (String) body.get("name"),
                (String) body.get("description"),
                (String) body.get("color")
        );
        auditService.log(p.getCompanyId(), p.getUserId(), "UPDATE_ROLE",
                "Role", id, null, "Updated role: " + role.getName());
        return ResponseEntity.ok(toMapWithPermissions(role));
    }

    /** PUT /roles/{id}/permissions — full permission replacement. */
    @PutMapping("/{id}/permissions")
    public ResponseEntity<Map<String, Object>> setPermissions(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "roles.assign_permissions");

        @SuppressWarnings("unchecked")
        List<Long> permIds = ((List<?>) body.get("permissionIds")).stream()
                .map(v -> Long.parseLong(v.toString()))
                .collect(Collectors.toList());

        roleService.updatePermissions(p.getCompanyId(), id, permIds);
        auditService.log(p.getCompanyId(), p.getUserId(), "SET_ROLE_PERMISSIONS",
                "Role", id, null, "Updated permissions for role #" + id);
        accessLogService.logAllowed(p.getCompanyId(), p.getUserId(), "roles.assign_permissions", "roles");
        Role role = roleService.getByIdForCompany(p.getCompanyId(), id);
        return ResponseEntity.ok(toMapWithPermissions(role));
    }

    /** DELETE /roles/{id} — remove a custom role. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal BizControlPrincipal p,
            @PathVariable Long id) {
        permissionService.require(p, "roles.delete");
        roleService.delete(p.getCompanyId(), id);
        auditService.log(p.getCompanyId(), p.getUserId(), "DELETE_ROLE",
                "Role", id, null, "Deleted role #" + id);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Map<String, Object> toMap(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          r.getId());
        m.put("name",        r.getName());
        m.put("code",        r.getCode());
        m.put("description", r.getDescription());
        m.put("isSystem",    r.isSystem());
        m.put("color",       r.getColor());
        return m;
    }

    private Map<String, Object> toMapWithPermissions(Role r) {
        Map<String, Object> m = toMap(r);
        m.put("permissions", r.getPermissions().stream()
                .map(perm -> Map.of(
                        "id", perm.getId(),
                        "code", perm.getCode(),
                        "groupName", perm.getGroupName(),
                        "description", perm.getDescription()
                ))
                .collect(Collectors.toList()));
        m.put("permissionCodes", r.getPermissions().stream()
                .map(perm -> perm.getCode())
                .collect(Collectors.toSet()));
        return m;
    }
}
