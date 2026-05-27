package uz.bizcontrol.production;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Product;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.ProductRepository;
import uz.bizcontrol.service.AuditService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BomService {

    private final BomTemplateRepository bomTemplateRepository;
    private final BomComponentRepository bomComponentRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    public List<BomTemplate> list(Long companyId) {
        return bomTemplateRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public BomTemplate getTemplate(Long companyId, Long id) {
        return bomTemplateRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("BOM"));
    }

    public List<BomComponent> components(Long bomTemplateId) {
        return bomComponentRepository.findByBomTemplateId(bomTemplateId);
    }

    public Map<String, Object> getOneWithComponents(Long companyId, Long id) {
        BomTemplate t = getTemplate(companyId, id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", t);
        m.put("components", components(id));
        return m;
    }

    @Transactional
    public BomTemplate create(Long companyId, Long userId, Map<String, Object> body) {
        Long productId = asLong(body.get("productId"));
        if (productId == null) throw new BusinessException("Finished product is required");
        validateProduct(companyId, productId);

        String name = str(body.get("name"));
        if (name == null || name.isBlank()) throw new BusinessException("BOM name is required");
        String version = str(body.getOrDefault("version", "v1"));
        BigDecimal outputQty = bd(body.get("outputQuantity"), BigDecimal.ONE);
        if (outputQty.signum() <= 0) throw new BusinessException("Output quantity must be greater than zero");

        if (bomTemplateRepository.existsByCompanyIdAndProductIdAndVersion(companyId, productId, version))
            throw new BusinessException("A BOM version '" + version + "' already exists for this product");

        BomTemplate t = bomTemplateRepository.save(BomTemplate.builder()
                .companyId(companyId).productId(productId).name(name).version(version)
                .outputQuantity(outputQty).unit(str(body.getOrDefault("unit", "piece")))
                .status("active").note(str(body.get("note"))).createdBy(userId)
                .build());

        saveComponents(companyId, t.getId(), body.get("components"), true);
        auditService.log(companyId, userId, "CREATE", "BomTemplate", t.getId(), null, "BOM: " + name);
        return t;
    }

    @Transactional
    public BomTemplate update(Long companyId, Long userId, Long id, Map<String, Object> body) {
        BomTemplate t = getTemplate(companyId, id);
        if (body.containsKey("name") && str(body.get("name")) != null) t.setName(str(body.get("name")));
        if (body.containsKey("outputQuantity")) {
            BigDecimal oq = bd(body.get("outputQuantity"), t.getOutputQuantity());
            if (oq.signum() <= 0) throw new BusinessException("Output quantity must be greater than zero");
            t.setOutputQuantity(oq);
        }
        if (body.containsKey("unit")) t.setUnit(str(body.get("unit")));
        if (body.containsKey("note")) t.setNote(str(body.get("note")));
        if (body.containsKey("status")) {
            String s = str(body.get("status"));
            if (s != null && List.of("active", "inactive").contains(s)) t.setStatus(s);
        }
        t = bomTemplateRepository.save(t);

        if (body.containsKey("components")) {
            bomComponentRepository.deleteByBomTemplateId(id);
            saveComponents(companyId, id, body.get("components"), true);
        }
        auditService.log(companyId, userId, "UPDATE", "BomTemplate", id, null, "BOM: " + t.getName());
        return t;
    }

    @Transactional
    public void delete(Long companyId, Long userId, Long id) {
        BomTemplate t = getTemplate(companyId, id);
        // Soft-deactivate to preserve history on any orders that referenced it.
        t.setStatus("inactive");
        bomTemplateRepository.save(t);
        auditService.log(companyId, userId, "DELETE", "BomTemplate", id, "active", "inactive");
    }

    /**
     * Compute the raw-material requirement for producing {@code plannedQty} finished
     * units from a BOM: required = componentQty × (plannedQty / outputQty) × (1 + waste%).
     * Returns un-persisted {@link ProductionOrderComponent} rows with unit/total cost filled
     * from each component product's current purchase price.
     */
    public List<ProductionOrderComponent> computeRequired(Long companyId, BomTemplate template,
                                                          BigDecimal plannedQty, Long sourceWarehouseId) {
        BigDecimal outputQty = template.getOutputQuantity().signum() > 0 ? template.getOutputQuantity() : BigDecimal.ONE;
        BigDecimal factor = plannedQty.divide(outputQty, 6, RoundingMode.HALF_UP);

        List<ProductionOrderComponent> result = new ArrayList<>();
        for (BomComponent c : components(template.getId())) {
            BigDecimal base = c.getQuantity().multiply(factor);
            BigDecimal wastePct = c.getWastePercent() != null ? c.getWastePercent() : BigDecimal.ZERO;
            BigDecimal wasteQty = base.multiply(wastePct).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            BigDecimal required = base.add(wasteQty).setScale(3, RoundingMode.HALF_UP);

            Product p = productRepository.findByCompanyIdAndId(companyId, c.getComponentProductId()).orElse(null);
            BigDecimal unitCost = p != null && p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO;
            BigDecimal totalCost = required.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

            result.add(ProductionOrderComponent.builder()
                    .productId(c.getComponentProductId())
                    .warehouseId(sourceWarehouseId)
                    .requiredQuantity(required)
                    .consumedQuantity(BigDecimal.ZERO)
                    .unit(c.getUnit())
                    .unitCost(unitCost)
                    .totalCost(totalCost)
                    .wastePercent(wastePct)
                    .wasteQuantity(wasteQty)
                    .build());
        }
        return result;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private void saveComponents(Long companyId, Long templateId, Object raw, boolean required) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            if (required) throw new BusinessException("A BOM needs at least one component");
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> cm)) continue;
            Long cpid = asLong(cm.get("componentProductId"));
            if (cpid == null) throw new BusinessException("Each component needs a product");
            validateProduct(companyId, cpid);
            BigDecimal qty = bd(cm.get("quantity"), BigDecimal.ZERO);
            if (qty.signum() <= 0) throw new BusinessException("Component quantity must be greater than zero");
            bomComponentRepository.save(BomComponent.builder()
                    .bomTemplateId(templateId)
                    .componentProductId(cpid)
                    .quantity(qty)
                    .unit(str(cmGet(cm, "unit", "piece")))
                    .wastePercent(bd(cm.get("wastePercent"), BigDecimal.ZERO))
                    .alternativeComponentId(asLong(cm.get("alternativeComponentId")))
                    .optional(Boolean.TRUE.equals(cm.get("isOptional")))
                    .note(str(cm.get("note")))
                    .build());
        }
    }

    private void validateProduct(Long companyId, Long productId) {
        productRepository.findByCompanyIdAndId(companyId, productId)
                .orElseThrow(() -> new BusinessException("Product " + productId + " not found"));
    }

    private static Object cmGet(Map<?, ?> m, String k, Object def) { Object v = m.get(k); return v != null ? v : def; }
    private static String str(Object o) { return o != null ? o.toString() : null; }
    private static Long asLong(Object o) { return o == null ? null : Long.valueOf(o.toString()); }
    private static BigDecimal bd(Object o, BigDecimal def) { return o == null ? def : new BigDecimal(o.toString()); }
}
