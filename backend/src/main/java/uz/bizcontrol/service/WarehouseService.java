package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Company;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.entity.Warehouse;
import uz.bizcontrol.entity.WarehouseStock;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.repository.WarehouseRepository;
import uz.bizcontrol.repository.WarehouseStockRepository;
import uz.bizcontrol.repository.StockMovementRepository;
import uz.bizcontrol.repository.StockTransferRepository;
import uz.bizcontrol.production.ProductionOrderRepository;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final Set<String> VALID_TYPES =
            Set.of("main", "retail", "transit", "damaged", "customs", "temporary");

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final WarehouseStockService warehouseStockService;
    private final CompanyService companyService;
    private final CountryService countryService;
    private final AuditService auditService;

    /** List all warehouses, guaranteeing at least the main warehouse exists. */
    @Transactional
    public List<Warehouse> list(Long companyId) {
        warehouseStockService.ensureMain(companyId);
        return warehouseRepository.findByCompanyIdOrderByNameAsc(companyId);
    }

    public Warehouse getOne(Long companyId, Long id) {
        return warehouseRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("Warehouse"));
    }

    @Transactional
    public Warehouse create(Long companyId, Long userId, Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) throw new BusinessException("Warehouse name is required");
        String type = str(body.get("type"));
        if (type == null || type.isBlank()) type = "main";
        if (!VALID_TYPES.contains(type)) throw new BusinessException("Invalid warehouse type: " + type);

        String code = str(body.get("code"));
        if (code != null && !code.isBlank() && warehouseRepository.existsByCompanyIdAndCode(companyId, code))
            throw new BusinessException("A warehouse with code '" + code + "' already exists");

        Long countryId = longOrNull(body.get("countryId"));
        countryService.requireExists(companyId, countryId);

        Company company = companyService.getById(companyId);
        Warehouse w = Warehouse.builder()
                .company(company)
                .name(name)
                .code(code)
                .location(str(body.get("location")))
                .countryId(countryId)
                .responsiblePerson(str(body.get("responsiblePerson")))
                .phone(str(body.get("phone")))
                .type(type)
                .status("active")
                .note(str(body.get("note")))
                .createdBy(userId)
                .build();
        w = warehouseRepository.save(w);
        auditService.log(companyId, userId, "CREATE", "Warehouse", w.getId(), null, "Warehouse: " + name);
        return w;
    }

    @Transactional
    public Warehouse update(Long companyId, Long userId, Long id, Map<String, Object> body) {
        Warehouse w = getOne(companyId, id);
        if (body.containsKey("name") && str(body.get("name")) != null) w.setName(str(body.get("name")));
        if (body.containsKey("location")) w.setLocation(str(body.get("location")));
        if (body.containsKey("countryId")) {
            Long cid = longOrNull(body.get("countryId"));
            countryService.requireExists(companyId, cid);
            w.setCountryId(cid);
        }
        if (body.containsKey("responsiblePerson")) w.setResponsiblePerson(str(body.get("responsiblePerson")));
        if (body.containsKey("phone")) w.setPhone(str(body.get("phone")));
        if (body.containsKey("note")) w.setNote(str(body.get("note")));
        if (body.containsKey("type")) {
            String type = str(body.get("type"));
            if (type != null && !VALID_TYPES.contains(type)) throw new BusinessException("Invalid warehouse type: " + type);
            if (type != null) w.setType(type);
        }
        if (body.containsKey("status")) {
            String status = str(body.get("status"));
            if (status != null && List.of("active", "inactive").contains(status)) w.setStatus(status);
        }
        w = warehouseRepository.save(w);
        auditService.log(companyId, userId, "UPDATE", "Warehouse", id, null, "Warehouse: " + w.getName());
        return w;
    }

    /** Archive a warehouse — blocked if it still holds stock. */
    @Transactional
    public Warehouse archive(Long companyId, Long userId, Long id) {
        Warehouse w = getOne(companyId, id);
        if ("archived".equals(w.getStatus())) throw new BusinessException("Warehouse is already archived");
        long withStock = warehouseStockRepository.countPositiveByWarehouse(id);
        if (withStock > 0)
            throw new BusinessException("Warehouse contains stock and cannot be archived. "
                    + "Transfer or remove its stock first.");
        String prev = w.getStatus();
        w.setStatus("archived");
        warehouseRepository.save(w);
        auditService.log(companyId, userId, "WAREHOUSE_ARCHIVED", "Warehouse", id, prev, "archived");
        return w;
    }

    /** Restore an archived warehouse back to active. */
    @Transactional
    public Warehouse restore(Long companyId, Long userId, Long id) {
        Warehouse w = getOne(companyId, id);
        if (!"archived".equals(w.getStatus()))
            throw new BusinessException("Only archived warehouses can be restored");
        w.setStatus("active");
        warehouseRepository.save(w);
        auditService.log(companyId, userId, "WAREHOUSE_RESTORED", "Warehouse", id, "archived", "active");
        return w;
    }

    /**
     * Permanently delete a warehouse — only when it holds no stock and is not
     * referenced by transfers or production orders. Zero-quantity stock rows
     * are removed first so the FK does not block deletion.
     */
    @Transactional
    public void delete(Long companyId, Long userId, Long id) {
        Warehouse w = getOne(companyId, id);
        if (warehouseStockRepository.countPositiveByWarehouse(id) > 0)
            throw new BusinessException("Warehouse contains stock and cannot be deleted. "
                    + "Transfer or remove its stock first.");
        if (stockTransferRepository.countByFromWarehouseIdOrToWarehouseId(id, id) > 0)
            throw new BusinessException("Warehouse has transfer history and cannot be deleted. Archive it instead.");
        if (productionOrderRepository.countUsingWarehouse(companyId, id) > 0)
            throw new BusinessException("Warehouse is used by production orders and cannot be deleted. Archive it instead.");

        List<WarehouseStock> rows = warehouseStockRepository.findByCompanyIdAndWarehouseId(companyId, id);
        if (!rows.isEmpty()) warehouseStockRepository.deleteAll(rows);
        warehouseRepository.delete(w);
        auditService.log(companyId, userId, "WAREHOUSE_DELETED", "Warehouse", id, w.getStatus(), "deleted");
    }

    /** Per-warehouse summary cards: product count, total qty, stock value, low-stock count. */
    public List<Map<String, Object>> summaries(Long companyId, boolean canSeeValue) {
        List<Warehouse> warehouses = list(companyId);
        Map<Long, Product> productCache = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Warehouse w : warehouses) {
            List<WarehouseStock> rows = warehouseStockRepository.findByCompanyIdAndWarehouseId(companyId, w.getId());
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalValue = BigDecimal.ZERO;
            int productCount = 0;
            int lowStock = 0;
            for (WarehouseStock r : rows) {
                if (r.getQuantity().compareTo(BigDecimal.ZERO) != 0) productCount++;
                totalQty = totalQty.add(r.getQuantity());
                Product p = productCache.computeIfAbsent(r.getProductId(),
                        pid -> productRepository.findById(pid).orElse(null));
                if (p != null) {
                    if (canSeeValue && p.getPurchasePrice() != null)
                        totalValue = totalValue.add(r.getQuantity().multiply(p.getPurchasePrice()));
                    if (p.getMinStockLevel() != null
                            && r.getQuantity().compareTo(p.getMinStockLevel()) <= 0) lowStock++;
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("name", w.getName());
            m.put("code", w.getCode());
            m.put("type", w.getType());
            m.put("status", w.getStatus());
            m.put("responsiblePerson", w.getResponsiblePerson());
            m.put("phone", w.getPhone());
            m.put("location", w.getLocation());
            m.put("countryId", w.getCountryId());
            m.put("productCount", productCount);
            m.put("totalQuantity", totalQty);
            m.put("lowStockCount", lowStock);
            if (canSeeValue) m.put("stockValue", totalValue);
            result.add(m);
        }
        return result;
    }

    /** Stock rows for one warehouse, enriched with product name/sku/unit. */
    public List<Map<String, Object>> warehouseStock(Long companyId, Long warehouseId, boolean canSeeValue) {
        getOne(companyId, warehouseId); // company-isolation check
        List<WarehouseStock> rows = warehouseStockRepository.findByCompanyIdAndWarehouseId(companyId, warehouseId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (WarehouseStock r : rows) {
            Product p = productRepository.findById(r.getProductId()).orElse(null);
            if (p == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", p.getId());
            m.put("productName", p.getName());
            m.put("sku", p.getSku());
            m.put("unit", p.getUnit());
            m.put("category", p.getCategory() != null ? p.getCategory().getName() : null);
            m.put("quantity", r.getQuantity());
            m.put("reservedQuantity", r.getReservedQuantity());
            m.put("availableQuantity", r.getAvailableQuantity());
            m.put("minStockLevel", p.getMinStockLevel());
            if (canSeeValue && p.getPurchasePrice() != null) {
                m.put("purchasePrice", p.getPurchasePrice());
                m.put("stockValue", r.getQuantity().multiply(p.getPurchasePrice()));
            }
            result.add(m);
        }
        return result;
    }

    private static String str(Object o) { return o != null ? o.toString() : null; }

    private static Long longOrNull(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        return Long.parseLong(s);
    }
}
