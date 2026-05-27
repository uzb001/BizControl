package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Permission;
import uz.bizcontrol.repository.PermissionRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionsController {

    private final PermissionRepository permissionRepository;
    private final PermissionService    permissionService;

    /**
     * GET /permissions — returns all permissions grouped by group_name.
     * Used by the Role Builder UI.
     */
    @GetMapping
    public ResponseEntity<Map<String, List<Map<String, Object>>>> listGrouped(
            @AuthenticationPrincipal BizControlPrincipal p) {

        permissionService.require(p, "roles.view");

        List<Permission> all = permissionRepository.findAllByOrderByGroupNameAscCodeAsc();

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Permission perm : all) {
            grouped
                .computeIfAbsent(perm.getGroupName(), k -> new ArrayList<>())
                .add(Map.of(
                    "id",          perm.getId(),
                    "code",        perm.getCode(),
                    "groupName",   perm.getGroupName(),
                    "description", perm.getDescription() != null ? perm.getDescription() : ""
                ));
        }
        return ResponseEntity.ok(grouped);
    }

    /**
     * GET /permissions/flat — flat list, no grouping.
     */
    @GetMapping("/flat")
    public ResponseEntity<List<Map<String, Object>>> listFlat(
            @AuthenticationPrincipal BizControlPrincipal p) {

        permissionService.require(p, "roles.view");

        return ResponseEntity.ok(
                permissionRepository.findAllByOrderByGroupNameAscCodeAsc()
                        .stream()
                        .map(perm -> Map.<String, Object>of(
                                "id",          perm.getId(),
                                "code",        perm.getCode(),
                                "groupName",   perm.getGroupName(),
                                "description", perm.getDescription() != null ? perm.getDescription() : ""
                        ))
                        .collect(Collectors.toList())
        );
    }
}
