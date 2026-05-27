package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.entity.Country;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CountryRepository;
import uz.bizcontrol.repository.SupplierRepository;
import uz.bizcontrol.repository.WarehouseRepository;

import java.util.List;
import java.util.Map;

/**
 * Manages the catalog of {@link Country countries} a company trades with. Countries are a
 * structured replacement for the legacy free-text supplier.country field; warehouses and
 * suppliers carry an optional FK ({@code country_id}) introduced in V15.
 *
 * <p>Lifecycle: active → archived (reversible) → deleted (only if no references).
 * Deletes are guarded against any warehouse or supplier still pointing at the row, so
 * archived countries that are still referenced can only be hidden, never destroyed.</p>
 */
@Service
@RequiredArgsConstructor
public class CountryService {

    private final CountryRepository countryRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Country> list(Long companyId, String status) {
        if (status != null && !status.isBlank()) {
            return countryRepository.findByCompanyIdAndStatusOrderByNameAsc(companyId, status);
        }
        return countryRepository.findByCompanyIdOrderByNameAsc(companyId);
    }

    @Transactional(readOnly = true)
    public Country getOne(Long companyId, Long id) {
        return countryRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> BusinessException.notFound("Country"));
    }

    @Transactional
    public Country create(Long companyId, Long userId, Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) throw new BusinessException("Country name is required");
        if (countryRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name.trim()))
            throw new BusinessException("A country named '" + name + "' already exists");

        String currency = str(body.get("currency"));
        if (currency == null || currency.isBlank()) currency = "UZS";

        Country c = Country.builder()
                .companyId(companyId)
                .name(name.trim())
                .code(str(body.get("code")))
                .currency(currency)
                .timezone(str(body.get("timezone")))
                .language(str(body.get("language")))
                .notes(str(body.get("notes")))
                .status("active")
                .createdBy(userId)
                .build();
        c = countryRepository.save(c);
        auditService.log(companyId, userId, "CREATE", "Country", c.getId(), null, "Country: " + c.getName());
        return c;
    }

    @Transactional
    public Country update(Long companyId, Long userId, Long id, Map<String, Object> body) {
        Country c = getOne(companyId, id);
        if (body.containsKey("name")) {
            String name = str(body.get("name"));
            if (name == null || name.isBlank()) throw new BusinessException("Country name cannot be empty");
            String trimmed = name.trim();
            if (!trimmed.equalsIgnoreCase(c.getName())
                    && countryRepository.existsByCompanyIdAndNameIgnoreCase(companyId, trimmed))
                throw new BusinessException("A country named '" + trimmed + "' already exists");
            c.setName(trimmed);
        }
        if (body.containsKey("code"))     c.setCode(str(body.get("code")));
        if (body.containsKey("currency")) c.setCurrency(defaultIfBlank(str(body.get("currency")), c.getCurrency()));
        if (body.containsKey("timezone")) c.setTimezone(str(body.get("timezone")));
        if (body.containsKey("language")) c.setLanguage(str(body.get("language")));
        if (body.containsKey("notes"))    c.setNotes(str(body.get("notes")));
        c = countryRepository.save(c);
        auditService.log(companyId, userId, "UPDATE", "Country", id, null, "Country: " + c.getName());
        return c;
    }

    @Transactional
    public Country archive(Long companyId, Long userId, Long id) {
        Country c = getOne(companyId, id);
        if ("archived".equals(c.getStatus())) throw new BusinessException("Country is already archived");
        String prev = c.getStatus();
        c.setStatus("archived");
        countryRepository.save(c);
        auditService.log(companyId, userId, "COUNTRY_ARCHIVED", "Country", id, prev, "archived");
        return c;
    }

    @Transactional
    public Country restore(Long companyId, Long userId, Long id) {
        Country c = getOne(companyId, id);
        if (!"archived".equals(c.getStatus()))
            throw new BusinessException("Only archived countries can be restored");
        c.setStatus("active");
        countryRepository.save(c);
        auditService.log(companyId, userId, "COUNTRY_RESTORED", "Country", id, "archived", "active");
        return c;
    }

    /**
     * Permanently delete a country only when no warehouse or supplier still references it.
     * If the country is still in use, the caller is expected to archive it instead.
     */
    @Transactional
    public void delete(Long companyId, Long userId, Long id) {
        Country c = getOne(companyId, id);
        long wh = warehouseRepository.countByCompanyIdAndCountryId(companyId, id);
        long sup = supplierRepository.countByCompanyIdAndCountryId(companyId, id);
        if (wh > 0 || sup > 0) {
            throw new BusinessException(
                    "Country is still referenced by " + wh + " warehouse(s) and " + sup
                            + " supplier(s). Reassign them first or archive the country instead.");
        }
        countryRepository.delete(c);
        auditService.log(companyId, userId, "COUNTRY_DELETED", "Country", id, c.getStatus(), "deleted");
    }

    /** Cheap helper used by warehouse/supplier services to validate FK on write. */
    @Transactional(readOnly = true)
    public void requireExists(Long companyId, Long countryId) {
        if (countryId == null) return;
        if (countryRepository.findByCompanyIdAndId(companyId, countryId).isEmpty())
            throw BusinessException.notFound("Country");
    }

    private static String str(Object o) { return o != null ? o.toString() : null; }

    private static String defaultIfBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
