package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Supplier;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.SupplierRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.CountryService;
import uz.bizcontrol.service.PermissionService;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;
    private final CompanyService companyService;
    private final CountryService countryService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Supplier>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Long countryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        permissionService.require(principal, "suppliers.view");
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Long companyId = principal.getCompanyId();
        Page<Supplier> result = supplierRepository.findAll(buildSpec(companyId, search, status, country, countryId), PageRequest.of(page, size, sort));

        // Mask supplier debt if lacking permission
        if (!permissionService.hasPermission(principal, "suppliers.view_debt")) {
            result.getContent().forEach(s -> s.setCurrentDebt(null));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Supplier> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Supplier supplier) {
        permissionService.require(principal, "suppliers.create");
        countryService.requireExists(principal.getCompanyId(), supplier.getCountryId());
        supplier.setCompany(companyService.getById(principal.getCompanyId()));
        supplier.setCreatedBy(principal.getUserId());
        supplier.setUpdatedBy(principal.getUserId());
        supplier = supplierRepository.save(supplier);
        auditService.log(principal.getCompanyId(), principal.getUserId(), "CREATE", "Supplier", supplier.getId(), null, supplier.getName());
        return ResponseEntity.ok(supplier);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getOne(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "suppliers.view");
        Supplier supplier = supplierRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("Supplier"));
        if (!permissionService.hasPermission(principal, "suppliers.view_debt")) {
            supplier.setCurrentDebt(null);
        }
        return ResponseEntity.ok(supplier);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Supplier updates) {
        permissionService.require(principal, "suppliers.edit");
        Supplier supplier = supplierRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("Supplier"));
        if (updates.getName() != null) supplier.setName(updates.getName());
        if (updates.getPhone() != null) supplier.setPhone(updates.getPhone());
        if (updates.getCountry() != null) supplier.setCountry(updates.getCountry());
        if (updates.getCountryId() != null) {
            countryService.requireExists(principal.getCompanyId(), updates.getCountryId());
            supplier.setCountryId(updates.getCountryId());
        }
        if (updates.getCity() != null) supplier.setCity(updates.getCity());
        if (updates.getStatus() != null) supplier.setStatus(updates.getStatus());
        supplier.setUpdatedBy(principal.getUserId());
        auditService.log(principal.getCompanyId(), principal.getUserId(), "UPDATE", "Supplier", id, null, supplier.getName());
        return ResponseEntity.ok(supplierRepository.save(supplier));
    }

    private org.springframework.data.jpa.domain.Specification<Supplier> buildSpec(
            Long companyId, String search, String status, String country, Long countryId) {
        return (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("company").get("id"), companyId));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like)
                ));
            }
            if (status != null && !status.isBlank()) preds.add(cb.equal(root.get("status"), status));
            if (country != null && !country.isBlank()) preds.add(cb.like(cb.lower(cb.coalesce(root.get("country"), "")), "%" + country.toLowerCase() + "%"));
            if (countryId != null) preds.add(cb.equal(root.get("countryId"), countryId));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
