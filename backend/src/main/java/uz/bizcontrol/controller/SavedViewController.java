package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.SavedView;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.SavedViewRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/saved-views")
@RequiredArgsConstructor
public class SavedViewController {

    private final SavedViewRepository savedViewRepository;
    private final CompanyService companyService;
    private final PermissionService permissionService;

    /** Permission required to READ/CREATE saved views for each module. */
    private static final Map<String, String> MODULE_VIEW_PERM = Map.ofEntries(
        Map.entry("sales",      "sales.view"),
        Map.entry("products",   "products.view"),
        Map.entry("stock",      "stock.view"),
        Map.entry("purchases",  "purchases.view"),
        Map.entry("customers",  "customers.view"),
        Map.entry("suppliers",  "suppliers.view"),
        Map.entry("debts",      "debts.view_customer"),
        Map.entry("reports",    "reports.view"),
        Map.entry("cashbox",    "cashbox.view"),
        Map.entry("bank",       "bank.view"),
        Map.entry("audit",      "audit.view"),
        Map.entry("approvals",  "approvals.view"),
        Map.entry("users",      "users.view"),
        Map.entry("roles",      "roles.view")
    );

    @GetMapping
    public ResponseEntity<List<SavedView>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String module) {

        Long cid = principal.getCompanyId();

        // If a specific module is requested, verify the caller may see that module
        if (module != null && !module.isBlank()) {
            String required = MODULE_VIEW_PERM.get(module);
            if (required != null) {
                permissionService.require(principal, required);
            }
            return ResponseEntity.ok(
                    savedViewRepository.findByCompanyIdAndModuleOrderByNameAsc(cid, module));
        }

        // Listing all saved views — filter to only modules the user can access
        List<SavedView> all = savedViewRepository.findByCompanyIdOrderByModuleAscNameAsc(cid);
        List<SavedView> visible = all.stream().filter(v -> {
            String req = MODULE_VIEW_PERM.get(v.getModule());
            return req == null || permissionService.hasPermission(principal, req);
        }).toList();
        return ResponseEntity.ok(visible);
    }

    @PostMapping
    public ResponseEntity<SavedView> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Map<String, Object> body) {

        String name = body.getOrDefault("name", "").toString();
        String module = body.getOrDefault("module", "").toString();
        String filterJson = body.getOrDefault("filterJson", "{}").toString();
        String columnJson = body.getOrDefault("columnJson", null) != null
                ? body.get("columnJson").toString() : null;
        String icon = body.getOrDefault("icon", null) != null ? body.get("icon").toString() : null;
        boolean shared = Boolean.parseBoolean(body.getOrDefault("isShared", "false").toString());

        if (name.isBlank()) throw new BusinessException("Name is required");
        if (module.isBlank()) throw new BusinessException("Module is required");

        // Must have view access to the target module
        String required = MODULE_VIEW_PERM.get(module);
        if (required != null) {
            permissionService.require(principal, required);
        }

        // Sharing a view requires settings-level permission
        if (shared) {
            permissionService.require(principal, "settings.view");
        }

        SavedView view = SavedView.builder()
                .company(companyService.getById(principal.getCompanyId()))
                .createdBy(principal.getUserId())
                .name(name)
                .module(module)
                .filterJson(filterJson)
                .columnJson(columnJson)
                .icon(icon)
                .isShared(shared)
                .build();

        return ResponseEntity.ok(savedViewRepository.save(view));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedView> update(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        SavedView view = savedViewRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("SavedView"));

        // Only creator or admin-level (settings.view permission) can update other users' views
        boolean canManageAll = permissionService.hasPermission(principal, "settings.view");
        if (!view.getCreatedBy().equals(principal.getUserId()) && !canManageAll) {
            throw new BusinessException("Not authorized to update this view");
        }

        if (body.containsKey("name")) view.setName(body.get("name").toString());
        if (body.containsKey("filterJson")) view.setFilterJson(body.get("filterJson").toString());
        if (body.containsKey("columnJson") && body.get("columnJson") != null)
            view.setColumnJson(body.get("columnJson").toString());
        if (body.containsKey("icon") && body.get("icon") != null)
            view.setIcon(body.get("icon").toString());
        if (body.containsKey("isShared"))
            view.setShared(Boolean.parseBoolean(body.get("isShared").toString()));

        return ResponseEntity.ok(savedViewRepository.save(view));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {

        SavedView view = savedViewRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("SavedView"));

        boolean canManageAll = permissionService.hasPermission(principal, "settings.view");
        if (!view.getCreatedBy().equals(principal.getUserId()) && !canManageAll) {
            throw new BusinessException("Not authorized to delete this view");
        }

        savedViewRepository.delete(view);
        return ResponseEntity.noContent().build();
    }
}
