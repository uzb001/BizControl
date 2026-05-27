package uz.bizcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportBatchRepository importBatchRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    // ── Parse file and return preview (first 20 rows, validated) ─────────
    @Transactional
    public Map<String, Object> preview(MultipartFile file, String module,
                                       Map<String, String> columnMapping,
                                       Long companyId, Long userId) throws IOException {

        List<Map<String, String>> rows = parseFile(file);
        if (rows.isEmpty()) throw new BusinessException("File is empty or has no data rows");

        List<Map<String, Object>> previewRows = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();
        int duplicates = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> raw = rows.get(i);
            Map<String, String> mapped = applyMapping(raw, columnMapping);
            List<String> rowErrors = validateRow(mapped, module, i + 2);
            boolean isDuplicate = checkDuplicate(mapped, module, companyId);

            Map<String, Object> previewRow = new LinkedHashMap<>(mapped);
            previewRow.put("_row", i + 2);
            previewRow.put("_valid", rowErrors.isEmpty() && !isDuplicate);
            previewRow.put("_errors", rowErrors);
            previewRow.put("_duplicate", isDuplicate);
            previewRows.add(previewRow);

            if (!rowErrors.isEmpty()) {
                for (String err : rowErrors) {
                    errors.add(Map.of("row", String.valueOf(i + 2), "error", err));
                }
            }
            if (isDuplicate) duplicates++;
        }

        // Save batch in preview state
        ImportBatch batch = ImportBatch.builder()
                .companyId(companyId)
                .module(module)
                .fileName(file.getOriginalFilename())
                .totalRows(rows.size())
                .failedRows(errors.size())
                .duplicateRows(duplicates)
                .status("preview")
                .createdBy(userId)
                .errorSummary(toJson(errors))
                .build();
        batch = importBatchRepository.save(batch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batch.getId());
        result.put("totalRows", rows.size());
        result.put("validRows", rows.size() - errors.size());
        result.put("invalidRows", errors.size());
        result.put("duplicateRows", duplicates);
        result.put("preview", previewRows.subList(0, Math.min(50, previewRows.size())));
        result.put("errors", errors.subList(0, Math.min(20, errors.size())));
        result.put("headers", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
        return result;
    }

    // ── Confirm import: actually create records ───────────────────────────
    @Transactional
    public Map<String, Object> confirm(Long batchId, MultipartFile file,
                                       String module, Map<String, String> columnMapping,
                                       boolean skipDuplicates, Long companyId, Long userId) throws IOException {

        ImportBatch batch = importBatchRepository.findByCompanyIdAndId(companyId, batchId)
                .orElseThrow(() -> new BusinessException("Import batch not found"));

        if (!"preview".equals(batch.getStatus())) {
            throw new BusinessException("Batch must be in preview state to confirm");
        }

        List<Map<String, String>> rows = parseFile(file);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException("Company not found"));

        List<Long> createdIds = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> raw = rows.get(i);
            Map<String, String> mapped = applyMapping(raw, columnMapping);
            List<String> rowErrors = validateRow(mapped, module, i + 2);

            if (!rowErrors.isEmpty()) {
                for (String err : rowErrors) errors.add(Map.of("row", String.valueOf(i + 2), "error", err));
                continue;
            }

            boolean isDuplicate = checkDuplicate(mapped, module, companyId);
            if (isDuplicate && skipDuplicates) {
                skipped++;
                continue;
            }

            try {
                Long id = createRecord(mapped, module, company, userId);
                createdIds.add(id);
            } catch (Exception e) {
                errors.add(Map.of("row", String.valueOf(i + 2), "error", e.getMessage()));
            }
        }

        batch.setStatus("confirmed");
        batch.setSuccessRows(createdIds.size());
        batch.setFailedRows(errors.size());
        batch.setRollbackData(toJson(Map.of("module", module, "ids", createdIds)));
        batch.setErrorSummary(toJson(errors));
        importBatchRepository.save(batch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batch.getId());
        result.put("imported", createdIds.size());
        result.put("skipped", skipped);
        result.put("failed", errors.size());
        result.put("errors", errors.subList(0, Math.min(10, errors.size())));
        return result;
    }

    // ── Rollback an import: delete all created records ────────────────────
    @Transactional
    public Map<String, Object> rollback(Long batchId, Long companyId) throws JsonProcessingException {
        ImportBatch batch = importBatchRepository.findByCompanyIdAndId(companyId, batchId)
                .orElseThrow(() -> new BusinessException("Import batch not found"));

        if (!"confirmed".equals(batch.getStatus())) {
            throw new BusinessException("Only confirmed imports can be rolled back");
        }

        Map<?, ?> rollbackData = objectMapper.readValue(batch.getRollbackData(), Map.class);
        String module = (String) rollbackData.get("module");
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) rollbackData.get("ids");

        int deleted = 0;
        for (Integer id : ids) {
            try {
                switch (module) {
                    case "products" -> productRepository.deleteById(id.longValue());
                    case "customers" -> customerRepository.deleteById(id.longValue());
                    case "suppliers" -> supplierRepository.deleteById(id.longValue());
                }
                deleted++;
            } catch (Exception ignored) {}
        }

        batch.setStatus("rolled_back");
        importBatchRepository.save(batch);

        return Map.of("rolledBack", deleted, "batchId", batchId);
    }

    // ── History ───────────────────────────────────────────────────────────
    public List<ImportBatch> history(Long companyId) {
        return importBatchRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    private List<Map<String, String>> parseFile(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".xlsx")) return parseXlsx(file);
        if (name.endsWith(".xls")) return parseXls(file);
        return parseCsv(file); // default: CSV
    }

    private List<Map<String, String>> parseXlsx(MultipartFile file) throws IOException {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            return parseSheet(wb.getSheetAt(0));
        }
    }

    private List<Map<String, String>> parseXls(MultipartFile file) throws IOException {
        try (Workbook wb = new HSSFWorkbook(file.getInputStream())) {
            return parseSheet(wb.getSheetAt(0));
        }
    }

    private List<Map<String, String>> parseSheet(Sheet sheet) {
        List<Map<String, String>> result = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return result;

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) headers.add(cellToString(cell));

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            boolean allEmpty = true;
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                String val = cell != null ? cellToString(cell) : "";
                rowMap.put(headers.get(j), val);
                if (!val.isBlank()) allEmpty = false;
            }
            if (!allEmpty) result.add(rowMap);
        }
        return result;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getLocalDateTimeCellValue().toString();
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue(); }
            }
            default -> "";
        };
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return result;
            String[] headers = splitCsvLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < values.length ? values[i].trim().replaceAll("^\"|\"$", "") : "");
                }
                result.add(row);
            }
        }
        return result;
    }

    private String[] splitCsvLine(String line) {
        // Handle quoted fields with commas inside
        List<String> tokens = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') inQuote = !inQuote;
            else if (c == ',' && !inQuote) { tokens.add(current.toString()); current.setLength(0); }
            else current.append(c);
        }
        tokens.add(current.toString());
        return tokens.toArray(new String[0]);
    }

    private Map<String, String> applyMapping(Map<String, String> rawRow, Map<String, String> mapping) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : rawRow.entrySet()) {
            String targetField = mapping.getOrDefault(e.getKey(), e.getKey());
            if (targetField != null && !targetField.isBlank()) {
                result.put(targetField, e.getValue());
            }
        }
        return result;
    }

    private List<String> validateRow(Map<String, String> row, String module, int rowNum) {
        List<String> errors = new ArrayList<>();
        switch (module) {
            case "products" -> {
                if (blank(row.get("name"))) errors.add("Row " + rowNum + ": 'name' is required");
                validateNumeric(row, "purchasePrice", rowNum, errors);
                validateNumeric(row, "sellingPrice", rowNum, errors);
                validateNumeric(row, "currentStock", rowNum, errors);
            }
            case "customers" -> {
                if (blank(row.get("name"))) errors.add("Row " + rowNum + ": 'name' is required");
            }
            case "suppliers" -> {
                if (blank(row.get("name"))) errors.add("Row " + rowNum + ": 'name' is required");
            }
        }
        return errors;
    }

    private void validateNumeric(Map<String, String> row, String field, int rowNum, List<String> errors) {
        String val = row.get(field);
        if (val != null && !val.isBlank()) {
            try { new BigDecimal(val.replace(",", ".")); }
            catch (NumberFormatException e) { errors.add("Row " + rowNum + ": '" + field + "' must be a number"); }
        }
    }

    private boolean checkDuplicate(Map<String, String> row, String module, Long companyId) {
        String name = row.getOrDefault("name", "");
        String sku = row.getOrDefault("sku", "");
        return switch (module) {
            case "products" -> !sku.isBlank()
                    ? productRepository.existsByCompanyIdAndSku(companyId, sku)
                    : productRepository.existsByCompanyIdAndName(companyId, name);
            case "customers" -> customerRepository.existsByCompanyIdAndName(companyId, name);
            case "suppliers" -> supplierRepository.existsByCompanyIdAndName(companyId, name);
            default -> false;
        };
    }

    private Long createRecord(Map<String, String> row, String module, Company company, Long userId) {
        return switch (module) {
            case "products" -> createProduct(row, company, userId);
            case "customers" -> createCustomer(row, company, userId);
            case "suppliers" -> createSupplier(row, company, userId);
            default -> throw new BusinessException("Unknown module: " + module);
        };
    }

    private Long createProduct(Map<String, String> row, Company company, Long userId) {
        Product p = Product.builder()
                .company(company)
                .name(row.getOrDefault("name", ""))
                .sku(blank(row.get("sku")) ? null : row.get("sku"))
                .barcode(blank(row.get("barcode")) ? null : row.get("barcode"))
                .unit(blank(row.get("unit")) ? "piece" : row.get("unit"))
                .purchasePrice(parseBD(row.get("purchasePrice")))
                .sellingPrice(parseBD(row.get("sellingPrice")))
                .currentStock(parseBD(row.get("currentStock")))
                .minStockLevel(parseBD(row.get("minStockLevel")))
                .description(row.get("description"))
                .createdBy(userId)
                .build();
        return productRepository.save(p).getId();
    }

    private Long createCustomer(Map<String, String> row, Company company, Long userId) {
        Customer c = Customer.builder()
                .company(company)
                .name(row.getOrDefault("name", ""))
                .phone(row.get("phone"))
                .city(row.get("city"))
                .customerType(blank(row.get("customerType")) ? "retail" : row.get("customerType"))
                .notes(row.get("notes"))
                .createdBy(userId)
                .build();
        return customerRepository.save(c).getId();
    }

    private Long createSupplier(Map<String, String> row, Company company, Long userId) {
        Supplier s = Supplier.builder()
                .company(company)
                .name(row.getOrDefault("name", ""))
                .phone(row.get("phone"))
                .country(row.get("country"))
                .city(row.get("city"))
                .createdBy(userId)
                .build();
        return supplierRepository.save(s).getId();
    }

    private BigDecimal parseBD(String val) {
        if (blank(val)) return BigDecimal.ZERO;
        try { return new BigDecimal(val.replace(",", ".")); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }
}
