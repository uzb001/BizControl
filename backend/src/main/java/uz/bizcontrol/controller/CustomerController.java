package uz.bizcontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Customer;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CustomerRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.AuditService;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<Customer>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        permissionService.require(principal, "customers.view");
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        Long companyId = principal.getCompanyId();

        Page<Customer> result = customerRepository.findAll(
                buildSpec(companyId, search, status, customerType, city), pageable);

        // Mask debt amount if lacking permission
        if (!permissionService.hasPermission(principal, "customers.view_debt")) {
            result.getContent().forEach(c -> c.setCurrentDebt(null));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Customer> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Customer customer) {
        permissionService.require(principal, "customers.create");
        customer.setCompany(companyService.getById(principal.getCompanyId()));
        customer.setCreatedBy(principal.getUserId());
        customer.setUpdatedBy(principal.getUserId());
        customer = customerRepository.save(customer);
        auditService.log(principal.getCompanyId(), principal.getUserId(), "CREATE", "Customer", customer.getId(), null, customer.getName());
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getOne(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "customers.view");
        Customer customer = customerRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("Customer"));
        if (!permissionService.hasPermission(principal, "customers.view_debt")) {
            customer.setCurrentDebt(null);
        }
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Customer updates) {
        permissionService.require(principal, "customers.edit");
        Customer customer = customerRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("Customer"));
        if (updates.getName() != null) customer.setName(updates.getName());
        if (updates.getPhone() != null) customer.setPhone(updates.getPhone());
        if (updates.getCity() != null) customer.setCity(updates.getCity());
        if (updates.getAddress() != null) customer.setAddress(updates.getAddress());
        if (updates.getCustomerType() != null) customer.setCustomerType(updates.getCustomerType());
        if (updates.getStatus() != null) customer.setStatus(updates.getStatus());
        if (updates.getNotes() != null) customer.setNotes(updates.getNotes());
        customer.setUpdatedBy(principal.getUserId());
        auditService.log(principal.getCompanyId(), principal.getUserId(), "UPDATE", "Customer", id, null, customer.getName());
        return ResponseEntity.ok(customerRepository.save(customer));
    }

    private org.springframework.data.jpa.domain.Specification<Customer> buildSpec(
            Long companyId, String search, String status, String customerType, String city) {
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
            if (customerType != null && !customerType.isBlank()) preds.add(cb.equal(root.get("customerType"), customerType));
            if (city != null && !city.isBlank()) preds.add(cb.like(cb.lower(root.get("city")), "%" + city.toLowerCase() + "%"));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
