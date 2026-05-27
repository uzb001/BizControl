package uz.bizcontrol.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import uz.bizcontrol.entity.*;
import uz.bizcontrol.repository.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final DebtRepository debtRepository;
    private final CashTransactionRepository cashTransactionRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    // ─── Products ──────────────────────────────────────────────────────────────

    public byte[] exportProducts(Long companyId, String companyName) throws IOException {
        List<Product> products = productRepository.findByCompanyIdAndStatus(companyId, "active");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Products");
            CellStyle hdr = headerStyle(wb);
            CellStyle title = titleStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, title, "Products Report — " + companyName, 10);
            writeDate(sheet);

            String[] headers = {"#", "Name", "SKU", "Barcode", "Category", "Supplier", "Unit",
                    "Buy Price", "Sell Price", "Wholesale", "Margin %", "Stock", "Min Stock", "Status"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal totalStock = BigDecimal.ZERO;
            BigDecimal totalValue = BigDecimal.ZERO;
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, p.getName());
                cell(row, 2, nvl(p.getSku()));
                cell(row, 3, nvl(p.getBarcode()));
                cell(row, 4, p.getCategory() != null ? p.getCategory().getName() : "");
                cell(row, 5, p.getSupplier() != null ? p.getSupplier().getName() : "");
                cell(row, 6, p.getUnit());
                cellNum(row, 7, p.getPurchasePrice(), money);
                cellNum(row, 8, p.getSellingPrice(), money);
                cellNum(row, 9, p.getWholesalePrice() != null ? p.getWholesalePrice() : BigDecimal.ZERO, money);
                cell(row, 10, p.getMarginPercent().toPlainString() + "%");
                cellNum(row, 11, p.getCurrentStock(), null);
                cellNum(row, 12, p.getMinStockLevel(), null);
                cell(row, 13, p.getStatus());
                totalStock = totalStock.add(p.getCurrentStock());
                totalValue = totalValue.add(p.getCurrentStock().multiply(p.getPurchasePrice()));
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 10, "TOTAL STOCK VALUE:", total);
            cellNum(totRow, 11, totalStock, total);
            cellNum(totRow, 12, totalValue, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Customers ─────────────────────────────────────────────────────────────

    public byte[] exportCustomers(Long companyId, String companyName) throws IOException {
        List<Customer> customers = customerRepository.findByCompanyIdAndStatus(companyId, "active");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Customers");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Customers — " + companyName, 8);
            writeDate(sheet);

            String[] headers = {"#", "Name", "Phone", "City", "Type", "Debt Limit", "Current Debt", "Status", "Created"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal totalDebt = BigDecimal.ZERO;
            for (int i = 0; i < customers.size(); i++) {
                Customer c = customers.get(i);
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, c.getName());
                cell(row, 2, nvl(c.getPhone()));
                cell(row, 3, nvl(c.getCity()));
                cell(row, 4, c.getCustomerType());
                cellNum(row, 5, c.getDebtLimit() != null ? c.getDebtLimit() : BigDecimal.ZERO, money);
                cellNum(row, 6, c.getCurrentDebt(), money);
                cell(row, 7, c.getStatus());
                cell(row, 8, c.getCreatedAt() != null ? c.getCreatedAt().format(DATE_FMT) : "");
                totalDebt = totalDebt.add(c.getCurrentDebt());
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 5, "TOTAL DEBT:", total);
            cellNum(totRow, 6, totalDebt, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Suppliers ─────────────────────────────────────────────────────────────

    public byte[] exportSuppliers(Long companyId, String companyName) throws IOException {
        List<Supplier> suppliers = supplierRepository.findByCompanyIdAndStatus(companyId, "active");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Suppliers");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Suppliers — " + companyName, 9);
            writeDate(sheet);

            String[] headers = {"#", "Name", "Contact", "Phone", "Email", "Country", "City", "Currency", "Current Debt", "Status"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal totalDebt = BigDecimal.ZERO;
            for (int i = 0; i < suppliers.size(); i++) {
                Supplier s = suppliers.get(i);
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, s.getName());
                cell(row, 2, nvl(s.getContactPerson()));
                cell(row, 3, nvl(s.getPhone()));
                cell(row, 4, nvl(s.getEmail()));
                cell(row, 5, nvl(s.getCountry()));
                cell(row, 6, nvl(s.getCity()));
                cell(row, 7, s.getCurrency());
                cellNum(row, 8, s.getCurrentDebt(), money);
                cell(row, 9, s.getStatus());
                totalDebt = totalDebt.add(s.getCurrentDebt());
            }
            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 7, "TOTAL:", total);
            cellNum(totRow, 8, totalDebt, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Sales ─────────────────────────────────────────────────────────────────

    public byte[] exportSales(Long companyId, String companyName, List<Sale> sales) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sales");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Sales Report — " + companyName, 9);
            writeDate(sheet);

            String[] headers = {"#", "Sale #", "Date", "Customer", "Items", "Total", "Paid", "Unpaid", "Method", "Status"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal sumTotal = BigDecimal.ZERO, sumPaid = BigDecimal.ZERO, sumUnpaid = BigDecimal.ZERO;
            for (int i = 0; i < sales.size(); i++) {
                Sale s = sales.get(i);
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, s.getSaleNumber());
                cell(row, 2, s.getSaleDate() != null ? s.getSaleDate().format(DATE_FMT) : "");
                cell(row, 3, s.getCustomer() != null ? s.getCustomer().getName() : "Walk-in");
                cell(row, 4, s.getItems() != null ? s.getItems().size() : 0);
                cellNum(row, 5, s.getTotalAmount(), money);
                cellNum(row, 6, s.getPaidAmount(), money);
                cellNum(row, 7, s.getUnpaidAmount(), money);
                cell(row, 8, s.getPaymentMethod());
                cell(row, 9, s.getStatus());
                sumTotal = sumTotal.add(s.getTotalAmount());
                sumPaid = sumPaid.add(s.getPaidAmount());
                sumUnpaid = sumUnpaid.add(s.getUnpaidAmount());
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 4, "TOTALS:", total);
            cellNum(totRow, 5, sumTotal, total);
            cellNum(totRow, 6, sumPaid, total);
            cellNum(totRow, 7, sumUnpaid, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Purchases ─────────────────────────────────────────────────────────────

    public byte[] exportPurchases(Long companyId, String companyName, List<Purchase> purchases) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Purchases");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Purchases Report — " + companyName, 9);
            writeDate(sheet);

            String[] headers = {"#", "PO #", "Date", "Supplier", "Items", "Total", "Paid", "Unpaid", "Method", "Status"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal sumTotal = BigDecimal.ZERO, sumPaid = BigDecimal.ZERO, sumUnpaid = BigDecimal.ZERO;
            for (int i = 0; i < purchases.size(); i++) {
                Purchase p = purchases.get(i);
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, p.getPurchaseNumber());
                cell(row, 2, p.getPurchaseDate() != null ? p.getPurchaseDate().format(DATE_FMT) : "");
                cell(row, 3, p.getSupplier() != null ? p.getSupplier().getName() : "—");
                cell(row, 4, p.getItems() != null ? p.getItems().size() : 0);
                cellNum(row, 5, p.getTotalAmount(), money);
                cellNum(row, 6, p.getPaidAmount(), money);
                cellNum(row, 7, p.getUnpaidAmount(), money);
                cell(row, 8, p.getPaymentMethod());
                cell(row, 9, p.getStatus());
                sumTotal = sumTotal.add(p.getTotalAmount());
                sumPaid = sumPaid.add(p.getPaidAmount());
                sumUnpaid = sumUnpaid.add(p.getUnpaidAmount());
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 4, "TOTALS:", total);
            cellNum(totRow, 5, sumTotal, total);
            cellNum(totRow, 6, sumPaid, total);
            cellNum(totRow, 7, sumUnpaid, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Debts ─────────────────────────────────────────────────────────────────

    public byte[] exportDebts(Long companyId, String companyName, List<uz.bizcontrol.entity.Debt> debts) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Debts");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Debts Report — " + companyName, 8);
            writeDate(sheet);

            String[] headers = {"#", "Type", "Party", "Original", "Paid", "Remaining", "Due Date", "Status"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal sumRemaining = BigDecimal.ZERO;
            for (int i = 0; i < debts.size(); i++) {
                uz.bizcontrol.entity.Debt d = debts.get(i);
                String party = "customer".equals(d.getDebtType())
                        ? (d.getCustomer() != null ? d.getCustomer().getName() : "—")
                        : (d.getSupplier() != null ? d.getSupplier().getName() : "—");
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, d.getDebtType());
                cell(row, 2, party);
                cellNum(row, 3, d.getOriginalAmount(), money);
                cellNum(row, 4, d.getPaidAmount(), money);
                cellNum(row, 5, d.getRemainingAmount(), money);
                cell(row, 6, d.getDueDate() != null ? d.getDueDate().format(DATE_FMT) : "—");
                cell(row, 7, d.getStatus());
                sumRemaining = sumRemaining.add(d.getRemainingAmount());
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 4, "TOTAL REMAINING:", total);
            cellNum(totRow, 5, sumRemaining, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Cash Transactions ─────────────────────────────────────────────────────

    public byte[] exportCashTransactions(Long companyId, String companyName, List<uz.bizcontrol.entity.CashTransaction> txns) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Cashbox");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Cashbox Report — " + companyName, 7);
            writeDate(sheet);

            String[] headers = {"#", "Date", "Type", "Source", "Category", "Amount", "Note"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
            for (int i = 0; i < txns.size(); i++) {
                uz.bizcontrol.entity.CashTransaction t = txns.get(i);
                Row row = sheet.createRow(rn++);
                cell(row, 0, i + 1);
                cell(row, 1, t.getTransactionDate() != null ? t.getTransactionDate().format(DATE_FMT) : "");
                cell(row, 2, t.getTransactionType());
                cell(row, 3, t.getPaymentSource());
                cell(row, 4, nvl(t.getCategory()));
                cellNum(row, 5, t.getAmount(), money);
                cell(row, 6, nvl(t.getNote()));
                if ("income".equals(t.getTransactionType())) income = income.add(t.getAmount());
                else expense = expense.add(t.getAmount());
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 4, "Income:", total);
            cellNum(totRow, 5, income, total);
            Row totRow2 = sheet.createRow(rn + 2);
            cell(totRow2, 4, "Expense:", total);
            cellNum(totRow2, 5, expense, total);
            Row totRow3 = sheet.createRow(rn + 3);
            cell(totRow3, 4, "Net:", total);
            cellNum(totRow3, 5, income.subtract(expense), total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Stock Report ──────────────────────────────────────────────────────────

    public byte[] exportStock(Long companyId, String companyName, List<Product> products) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Stock");
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);
            CellStyle warn = warningStyle(wb);

            writeTitle(sheet, titleStyle(wb), "Stock Report — " + companyName, 8);
            writeDate(sheet);

            String[] headers = {"#", "Name", "SKU", "Category", "Unit", "Stock", "Min", "Value", "Status"};
            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            BigDecimal totalValue = BigDecimal.ZERO;
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                Row row = sheet.createRow(rn++);
                boolean isLow = p.getCurrentStock().compareTo(p.getMinStockLevel()) <= 0;
                boolean isOut = p.getCurrentStock().compareTo(BigDecimal.ZERO) == 0;
                CellStyle rowStyle = (isOut || isLow) ? warn : null;

                cell(row, 0, i + 1);
                cell(row, 1, p.getName());
                cell(row, 2, nvl(p.getSku()));
                cell(row, 3, p.getCategory() != null ? p.getCategory().getName() : "");
                cell(row, 4, p.getUnit());
                cellNum(row, 5, p.getCurrentStock(), isLow ? warn : null);
                cellNum(row, 6, p.getMinStockLevel(), null);
                BigDecimal val = p.getCurrentStock().multiply(p.getPurchasePrice());
                cellNum(row, 7, val, money);
                cell(row, 8, isOut ? "OUT OF STOCK" : isLow ? "LOW STOCK" : "OK");
                totalValue = totalValue.add(val);
            }

            Row totRow = sheet.createRow(rn + 1);
            cell(totRow, 6, "TOTAL VALUE:", total);
            cellNum(totRow, 7, totalValue, total);

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Generic report engine ───────────────────────────────────────────────────
    //
    // A single reusable builder used by every "phase-1" export (categories, stock
    // movements, accounting statements, audit logs, users, roles, daily close, and
    // the smart reports). Produces a professional sheet with: company name + title,
    // generated date + generated-by, filter summary, frozen header row, money
    // formatting on the requested columns, and an optional bold totals row.

    public byte[] genericReport(String companyName, String reportTitle, String generatedBy,
                                String filterSummary, String[] headers, List<Object[]> rows,
                                Object[] totalsRow, int[] moneyCols) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(safeSheetName(reportTitle));
            CellStyle hdr = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle total = totalStyle(wb);

            Set<Integer> moneySet = new HashSet<>();
            if (moneyCols != null) for (int c : moneyCols) moneySet.add(c);

            int lastCol = Math.max(1, headers.length - 1);
            writeTitle(sheet, titleStyle(wb), reportTitle + " — " + companyName, lastCol);
            writeMeta(sheet, generatedBy, filterSummary);

            Row hr = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) cell(hr, i, headers[i], hdr);

            int rn = 4;
            if (rows != null) {
                for (Object[] r : rows) {
                    Row row = sheet.createRow(rn++);
                    for (int i = 0; i < r.length; i++) {
                        writeCell(row, i, r[i], moneySet.contains(i) ? money : null);
                    }
                }
            }

            if (totalsRow != null) {
                Row tr = sheet.createRow(rn + 1);
                for (int i = 0; i < totalsRow.length; i++) {
                    if (totalsRow[i] == null) continue;
                    writeCell(tr, i, totalsRow[i], total);
                }
            }

            sheet.createFreezePane(0, 4);
            autoSize(sheet, headers.length);
            return toBytes(wb);
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void writeMeta(Sheet sheet, String generatedBy, String filterSummary) {
        sheet.createRow(1).createCell(0).setCellValue(
                "Generated: " + LocalDateTime.now().format(DATE_FMT)
                        + (generatedBy != null && !generatedBy.isBlank() ? "   |   By: " + generatedBy : ""));
        sheet.createRow(2).createCell(0).setCellValue(
                "Filters: " + (filterSummary != null && !filterSummary.isBlank() ? filterSummary : "None"));
    }

    private void writeCell(Row row, int col, Object val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val == null) c.setCellValue("");
        else if (val instanceof BigDecimal bd) c.setCellValue(bd.doubleValue());
        else if (val instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(val.toString());
        if (style != null) c.setCellStyle(style);
    }

    private String safeSheetName(String s) {
        String n = (s == null ? "Report" : s).replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (n.isEmpty()) n = "Report";
        return n.length() > 31 ? n.substring(0, 31) : n;
    }

    private void writeTitle(Sheet sheet, CellStyle style, String title, int cols) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols));
    }

    private void writeDate(Sheet sheet) {
        sheet.createRow(1).createCell(0)
                .setCellValue("Generated: " + LocalDateTime.now().format(DATE_FMT));
    }

    private void cell(Row row, int col, String val) {
        row.createCell(col).setCellValue(val != null ? val : "");
    }

    private void cell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        if (style != null) c.setCellStyle(style);
    }

    private void cell(Row row, int col, int val) {
        row.createCell(col).setCellValue(val);
    }

    private void cellNum(Row row, int col, BigDecimal val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val.doubleValue() : 0.0);
        if (style != null) c.setCellStyle(style);
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle totalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle warningStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }
}
