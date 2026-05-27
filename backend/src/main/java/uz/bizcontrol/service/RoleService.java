package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.entity.Permission;
import uz.bizcontrol.entity.Role;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.PermissionRepository;
import uz.bizcontrol.repository.RoleRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;

    // ── Default role templates ─────────────────────────────────────

    /** All permission codes that exist in the system. */
    private static final List<String> ALL_PERMISSIONS = List.of(
        "dashboard.view",
        "products.view","products.create","products.edit","products.delete",
        "products.export","products.view_purchase_price",
        "stock.view","stock.adjust","stock.view_movements","stock.export",
        "warehouses.view","warehouses.create","warehouses.edit","warehouses.archive",
        "warehouse_stock.view","warehouse_stock.adjust","warehouse_stock.transfer",
        "warehouse_stock.view_movements","warehouse_stock.export",
        "production.view","production.create","production.edit","production.cancel",
        "production.start","production.complete","production.quality_check","production.export","production.view_cost",
        "bom.view","bom.create","bom.edit","bom.delete","bom.export",
        "production_waste.view","production_waste.create",
        "sales.view","sales.create","sales.edit","sales.cancel","sales.add_payment",
        "sales.view_profit","sales.discount","sales.export",
        "purchases.view","purchases.create","purchases.edit","purchases.cancel",
        "purchases.add_payment","purchases.export",
        "customers.view","customers.create","customers.edit","customers.delete",
        "customers.view_debt","customers.export",
        "suppliers.view","suppliers.create","suppliers.edit","suppliers.delete",
        "suppliers.view_debt","suppliers.export",
        "cashbox.view","cashbox.create_income","cashbox.create_expense",
        "cashbox.edit","cashbox.cancel","cashbox.export",
        "bank.view","bank.create","bank.edit","bank.export",
        "debts.view_customer","debts.view_supplier","debts.add_payment",
        "debts.close","debts.export",
        "reports.view","reports.view_profit","reports.view_money_leak",
        "reports.view_customer_scores","reports.view_dead_stock","reports.export",
        "import.view","import.upload","import.confirm","import.rollback",
        "export.all",
        "users.view","users.invite","users.change_role","users.deactivate","users.remove",
        "roles.view","roles.create","roles.edit","roles.delete","roles.assign_permissions",
        "settings.view","settings.edit_company","settings.billing","settings.security",
        "audit.view","audit.export",
        "daily_close.view","daily_close.create",
        "approvals.view","approvals.request","approvals.decide"
    );

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
        "OWNER",   ALL_PERMISSIONS,
        "ADMIN",   List.of(
            "dashboard.view",
            "products.view","products.create","products.edit","products.delete","products.export","products.view_purchase_price",
            "stock.view","stock.adjust","stock.view_movements","stock.export",
            "warehouses.view","warehouses.create","warehouses.edit","warehouses.archive",
            "warehouse_stock.view","warehouse_stock.adjust","warehouse_stock.transfer","warehouse_stock.view_movements","warehouse_stock.export",
            "production.view","production.create","production.edit","production.cancel","production.start","production.complete","production.quality_check","production.export","production.view_cost",
            "bom.view","bom.create","bom.edit","bom.delete","bom.export","production_waste.view","production_waste.create",
            "sales.view","sales.create","sales.edit","sales.cancel","sales.add_payment","sales.view_profit","sales.discount","sales.export",
            "purchases.view","purchases.create","purchases.edit","purchases.cancel","purchases.add_payment","purchases.export",
            "customers.view","customers.create","customers.edit","customers.view_debt","customers.export",
            "suppliers.view","suppliers.create","suppliers.edit","suppliers.view_debt","suppliers.export",
            "cashbox.view","cashbox.create_income","cashbox.create_expense","cashbox.edit","cashbox.cancel","cashbox.export",
            "bank.view","bank.create","bank.edit","bank.export",
            "debts.view_customer","debts.view_supplier","debts.add_payment","debts.close","debts.export",
            "reports.view","reports.view_profit","reports.view_money_leak","reports.view_customer_scores","reports.view_dead_stock","reports.export",
            "import.view","import.upload","import.confirm","import.rollback",
            "export.all",
            "users.view","users.invite","users.change_role","users.deactivate",
            "roles.view",
            "settings.view","settings.edit_company",
            "audit.view","audit.export",
            "daily_close.view","daily_close.create",
            "approvals.view","approvals.request","approvals.decide"
        ),
        "MANAGER", List.of(
            "dashboard.view",
            "products.view","products.create","products.edit","products.export","products.view_purchase_price",
            "stock.view","stock.adjust","stock.view_movements","stock.export",
            "warehouses.view","warehouses.create","warehouses.edit",
            "warehouse_stock.view","warehouse_stock.adjust","warehouse_stock.transfer","warehouse_stock.view_movements","warehouse_stock.export",
            "production.view","production.create","production.edit","production.start","production.complete","production.quality_check","production.export","production.view_cost",
            "bom.view","bom.create","bom.edit","bom.export","production_waste.view","production_waste.create",
            "sales.view","sales.create","sales.edit","sales.cancel","sales.add_payment","sales.view_profit","sales.discount","sales.export",
            "purchases.view","purchases.create","purchases.add_payment","purchases.export",
            "customers.view","customers.create","customers.edit","customers.view_debt","customers.export",
            "suppliers.view","suppliers.view_debt",
            "cashbox.view","cashbox.create_income","cashbox.create_expense",
            "debts.view_customer","debts.add_payment",
            "reports.view","reports.view_profit","reports.view_dead_stock",
            "import.view","import.upload","import.confirm",
            "users.view",
            "settings.view",
            "daily_close.view","daily_close.create",
            "approvals.view","approvals.request"
        ),
        "SELLER", List.of(
            "dashboard.view",
            "products.view",
            "stock.view",
            "warehouse_stock.view",
            "sales.view","sales.create","sales.add_payment","sales.discount",
            "customers.view","customers.create","customers.edit","customers.view_debt",
            "debts.view_customer"
        ),
        "ACCOUNTANT", List.of(
            "dashboard.view",
            "warehouses.view","warehouse_stock.view","warehouse_stock.export",
            "production.view","production.view_cost","production.export","bom.view","production_waste.view",
            "cashbox.view","cashbox.create_income","cashbox.create_expense","cashbox.edit","cashbox.cancel","cashbox.export",
            "bank.view","bank.create","bank.edit","bank.export",
            "debts.view_customer","debts.view_supplier","debts.add_payment","debts.close","debts.export",
            "reports.view","reports.view_profit","reports.view_money_leak","reports.export",
            "audit.view",
            "daily_close.view","daily_close.create"
        ),
        "WAREHOUSE", List.of(
            "dashboard.view",
            "products.view",
            "stock.view","stock.adjust","stock.view_movements","stock.export",
            "warehouses.view","warehouse_stock.view","warehouse_stock.adjust","warehouse_stock.transfer","warehouse_stock.view_movements","warehouse_stock.export",
            "production.view","production.start","production.complete","production.quality_check","bom.view","production_waste.view","production_waste.create",
            "purchases.view"
        ),
        "CASHIER", List.of(
            "dashboard.view",
            "sales.view","sales.create","sales.add_payment",
            "warehouse_stock.view",
            "cashbox.view","cashbox.create_income",
            "customers.view","customers.create",
            "debts.view_customer"
        ),
        "AUDITOR", List.of(
            "dashboard.view",
            "products.view","stock.view","stock.view_movements",
            "warehouses.view","warehouse_stock.view","warehouse_stock.view_movements","warehouse_stock.export",
            "production.view","production.view_cost","production.export","bom.view","production_waste.view",
            "sales.view","purchases.view",
            "cashbox.view","bank.view",
            "debts.view_customer","debts.view_supplier",
            "reports.view","reports.view_profit","reports.view_money_leak",
            "reports.view_customer_scores","reports.view_dead_stock","reports.export",
            "audit.view","audit.export"
        ),
        "READ_ONLY", List.of(
            "dashboard.view",
            "products.view","stock.view","warehouse_stock.view","sales.view","purchases.view",
            "production.view","bom.view",
            "customers.view","suppliers.view","debts.view_customer"
        )
    );

    private static final Map<String, String> ROLE_COLORS = Map.of(
        "OWNER",      "#7c3aed",
        "ADMIN",      "#2563eb",
        "MANAGER",    "#0891b2",
        "SELLER",     "#16a34a",
        "ACCOUNTANT", "#d97706",
        "WAREHOUSE",  "#9333ea",
        "CASHIER",    "#db2777",
        "AUDITOR",    "#64748b",
        "READ_ONLY",  "#94a3b8"
    );

    // ── Public API ─────────────────────────────────────────────────

    public List<Role> listForCompany(Long companyId) {
        return roleRepository.findByCompanyIdOrderByNameAsc(companyId);
    }

    public Role getByIdForCompany(Long companyId, Long roleId) {
        return roleRepository.findByCompanyIdAndId(companyId, roleId)
                .orElseThrow(() -> BusinessException.notFound("Role"));
    }

    public Role getOwnerRole(Long companyId) {
        return roleRepository.findByCompanyIdAndCode(companyId, "OWNER")
                .orElseThrow(() -> new IllegalStateException("OWNER role missing for company " + companyId));
    }

    public Optional<Role> findByCodeForCompany(Long companyId, String code) {
        return roleRepository.findByCompanyIdAndCode(companyId, code.toUpperCase());
    }

    @Transactional
    public Role create(Long companyId, String name, String code, String description, String color,
                       List<Long> permissionIds) {
        if (roleRepository.existsByCompanyIdAndCode(companyId, code.toUpperCase())) {
            throw new BusinessException("A role with code '" + code + "' already exists");
        }
        Company company = new Company();
        company.setId(companyId);

        Role role = Role.builder()
                .company(company)
                .name(name)
                .code(code.toUpperCase())
                .description(description)
                .color(color != null ? color : "#6366f1")
                .isSystem(false)
                .build();

        if (permissionIds != null && !permissionIds.isEmpty()) {
            Set<Permission> perms = new HashSet<>(permissionRepository.findAllById(permissionIds));
            role.setPermissions(perms);
        }
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(Long companyId, Long roleId, String name, String description, String color) {
        Role role = getByIdForCompany(companyId, roleId);
        if (role.isSystem() && "OWNER".equals(role.getCode())) {
            throw new BusinessException("Cannot modify the OWNER role");
        }
        if (name != null) role.setName(name);
        if (description != null) role.setDescription(description);
        if (color != null) role.setColor(color);
        return roleRepository.save(role);
    }

    @Transactional
    public void updatePermissions(Long companyId, Long roleId, List<Long> permissionIds) {
        Role role = getByIdForCompany(companyId, roleId);
        if ("OWNER".equals(role.getCode())) {
            throw new BusinessException("Cannot restrict OWNER permissions");
        }
        Set<Permission> perms = new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.setPermissions(perms);
        roleRepository.save(role);
        permissionService.invalidateCache(roleId);
    }

    @Transactional
    public void delete(Long companyId, Long roleId) {
        Role role = getByIdForCompany(companyId, roleId);
        if (role.isSystem()) {
            throw new BusinessException("Cannot delete a system role. You can edit its permissions instead.");
        }
        if (roleRepository.countActiveUsersByRole(roleId) > 0) {
            throw new BusinessException("Cannot delete a role that is assigned to active users");
        }
        roleRepository.delete(role);
        permissionService.invalidateCache(roleId);
    }

    // ── Bootstrap default roles for a new company ──────────────────

    @Transactional
    public Map<String, Role> createDefaultRoles(Company company) {
        Map<String, Permission> permByCode = permissionRepository.findAll()
                .stream().collect(Collectors.toMap(Permission::getCode, p -> p));

        Map<String, Role> created = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : ROLE_PERMISSIONS.entrySet()) {
            String code = entry.getKey();
            List<String> perms = entry.getValue();

            Set<Permission> permSet = perms.stream()
                    .map(permByCode::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Role role = Role.builder()
                    .company(company)
                    .name(friendlyName(code))
                    .code(code)
                    .description(defaultDesc(code))
                    .isSystem(true)
                    .color(ROLE_COLORS.getOrDefault(code, "#6366f1"))
                    .permissions(permSet)
                    .build();

            created.put(code, roleRepository.save(role));
        }
        return created;
    }

    private String friendlyName(String code) {
        return switch (code) {
            case "OWNER"      -> "CEO / Owner";
            case "ADMIN"      -> "Admin";
            case "MANAGER"    -> "Manager";
            case "SELLER"     -> "Seller / Do'konchi";
            case "ACCOUNTANT" -> "Accountant / Buxgalter";
            case "WAREHOUSE"  -> "Warehouse / Skladchi";
            case "CASHIER"    -> "Cashier / Kassir";
            case "AUDITOR"    -> "Auditor";
            case "READ_ONLY"  -> "Read Only";
            default           -> code;
        };
    }

    private String defaultDesc(String code) {
        return switch (code) {
            case "OWNER"      -> "Full access to everything in the company";
            case "ADMIN"      -> "Full operational access, no billing or security";
            case "MANAGER"    -> "Manages sales, purchases, staff operations";
            case "SELLER"     -> "Creates sales, manages customers, no pricing visibility";
            case "ACCOUNTANT" -> "Manages cash, bank, debts and financial reports";
            case "WAREHOUSE"  -> "Manages products and stock, no financial data";
            case "CASHIER"    -> "Handles sales and cash register only";
            case "AUDITOR"    -> "Read-only access to all financial data";
            case "READ_ONLY"  -> "View-only access to basic modules";
            default           -> "";
        };
    }
}
