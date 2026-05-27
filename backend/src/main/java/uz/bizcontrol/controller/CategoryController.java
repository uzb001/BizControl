package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.Category;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CategoryRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CompanyService companyService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<Category>> list(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "products.view");
        return ResponseEntity.ok(categoryRepository.findByCompanyId(principal.getCompanyId()));
    }

    @PostMapping
    public ResponseEntity<Category> create(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Category category) {
        permissionService.require(principal, "products.create");
        category.setCompany(companyService.getById(principal.getCompanyId()));
        category.setCreatedBy(principal.getUserId());
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> update(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id,
            @RequestBody Category updates) {
        permissionService.require(principal, "products.edit");
        Category cat = categoryRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("Category"));
        if (updates.getName() != null) cat.setName(updates.getName());
        if (updates.getDescription() != null) cat.setDescription(updates.getDescription());
        if (updates.getStatus() != null) cat.setStatus(updates.getStatus());
        return ResponseEntity.ok(categoryRepository.save(cat));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {
        permissionService.require(principal, "products.delete");
        Category cat = categoryRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("Category"));
        cat.setStatus("inactive");
        categoryRepository.save(cat);
        return ResponseEntity.ok().build();
    }
}
