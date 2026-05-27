package uz.bizcontrol.production;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/production/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<BomTemplate>> list(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "bom.view");
        return ResponseEntity.ok(bomService.list(p.getCompanyId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "bom.view");
        return ResponseEntity.ok(bomService.getOneWithComponents(p.getCompanyId(), id));
    }

    @PostMapping
    public ResponseEntity<BomTemplate> create(@AuthenticationPrincipal BizControlPrincipal p,
                                              @RequestBody Map<String, Object> body) {
        permissionService.require(p, "bom.create");
        return ResponseEntity.ok(bomService.create(p.getCompanyId(), p.getUserId(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BomTemplate> update(@AuthenticationPrincipal BizControlPrincipal p,
                                              @PathVariable Long id, @RequestBody Map<String, Object> body) {
        permissionService.require(p, "bom.edit");
        return ResponseEntity.ok(bomService.update(p.getCompanyId(), p.getUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal BizControlPrincipal p, @PathVariable Long id) {
        permissionService.require(p, "bom.delete");
        bomService.delete(p.getCompanyId(), p.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
