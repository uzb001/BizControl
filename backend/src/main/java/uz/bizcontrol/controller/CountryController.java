package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Country;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.CountryService;
import uz.bizcontrol.service.PermissionService;

import java.util.List;
import java.util.Map;

/** REST surface for {@link Country} CRUD + archive/restore lifecycle. */
@RestController
@RequestMapping("/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<Country>> list(@AuthenticationPrincipal BizControlPrincipal p,
                                              @RequestParam(required = false) String status) {
        permissionService.require(p, "countries.view");
        return ResponseEntity.ok(countryService.list(p.getCompanyId(), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Country> get(@AuthenticationPrincipal BizControlPrincipal p,
                                       @PathVariable Long id) {
        permissionService.require(p, "countries.view");
        return ResponseEntity.ok(countryService.getOne(p.getCompanyId(), id));
    }

    @PostMapping
    public ResponseEntity<Country> create(@AuthenticationPrincipal BizControlPrincipal p,
                                          @RequestBody Map<String, Object> body) {
        permissionService.require(p, "countries.create");
        return ResponseEntity.ok(countryService.create(p.getCompanyId(), p.getUserId(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Country> update(@AuthenticationPrincipal BizControlPrincipal p,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        permissionService.require(p, "countries.edit");
        return ResponseEntity.ok(countryService.update(p.getCompanyId(), p.getUserId(), id, body));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Country> archive(@AuthenticationPrincipal BizControlPrincipal p,
                                           @PathVariable Long id) {
        permissionService.require(p, "countries.archive");
        return ResponseEntity.ok(countryService.archive(p.getCompanyId(), p.getUserId(), id));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Country> restore(@AuthenticationPrincipal BizControlPrincipal p,
                                           @PathVariable Long id) {
        permissionService.require(p, "countries.archive");
        return ResponseEntity.ok(countryService.restore(p.getCompanyId(), p.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal BizControlPrincipal p,
                                       @PathVariable Long id) {
        permissionService.require(p, "countries.archive");
        countryService.delete(p.getCompanyId(), p.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
