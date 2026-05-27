package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.DailyClose;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.CashTransactionRepository;
import uz.bizcontrol.repository.DailyCloseRepository;
import uz.bizcontrol.repository.SaleRepository;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.CompanyService;
import uz.bizcontrol.service.PermissionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@RestController
@RequestMapping("/daily-close")
@RequiredArgsConstructor
public class DailyCloseController {

    private final DailyCloseRepository dailyCloseRepository;
    private final SaleRepository saleRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final CompanyService companyService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Page<DailyClose>> list(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        permissionService.require(principal, "daily_close.view");
        return ResponseEntity.ok(dailyCloseRepository.findByCompanyIdOrderByCloseDateDesc(
                principal.getCompanyId(), PageRequest.of(page, size)));
    }

    @GetMapping("/prepare")
    public ResponseEntity<Map<String, Object>> prepare(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestParam(required = false) String date) {

        permissionService.require(principal, "daily_close.create");
        Long cid = principal.getCompanyId();
        LocalDate closeDate = date != null ? LocalDate.parse(date) : LocalDate.now();

        if (dailyCloseRepository.existsByCompanyIdAndCloseDateAndStatus(cid, closeDate, "closed")) {
            throw new BusinessException("Day " + closeDate + " is already closed");
        }

        LocalDateTime from = closeDate.atStartOfDay();
        LocalDateTime to = closeDate.atTime(LocalTime.MAX);

        BigDecimal daySales = saleRepository.sumTotalByCompanyAndDateRange(cid, from, to);
        BigDecimal dayProfit = saleRepository.sumProfitByCompanyAndDateRange(cid, from, to);
        BigDecimal cashIn = cashTransactionRepository.sumByTypeAndSourceAndDateRange(cid, "income", "cash", from, to);
        BigDecimal cashOut = cashTransactionRepository.sumByTypeAndSourceAndDateRange(cid, "expense", "cash", from, to);
        BigDecimal expectedCash = companyService.getById(cid).getCashBalance();

        DailyClose existing = dailyCloseRepository.findByCompanyIdAndCloseDate(cid, closeDate).orElse(null);

        return ResponseEntity.ok(Map.of(
                "closeDate", closeDate.toString(),
                "alreadyClosed", existing != null && "closed".equals(existing.getStatus()),
                "existingId", existing != null ? existing.getId() : null,
                "totalSales", daySales,
                "totalExpenses", cashOut,
                "totalProfit", dayProfit,
                "cashIn", cashIn,
                "cashOut", cashOut,
                "expectedCash", expectedCash
        ));
    }

    @PostMapping
    public ResponseEntity<DailyClose> createOrUpdate(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @RequestBody Map<String, Object> body) {

        permissionService.require(principal, "daily_close.create");
        Long cid = principal.getCompanyId();
        LocalDate closeDate = LocalDate.parse(body.getOrDefault("closeDate", LocalDate.now().toString()).toString());

        DailyClose dc = dailyCloseRepository.findByCompanyIdAndCloseDate(cid, closeDate).orElse(null);

        if (dc != null && "closed".equals(dc.getStatus())) {
            throw new BusinessException("Day " + closeDate + " is already closed and cannot be modified");
        }

        BigDecimal actualCash   = new BigDecimal(body.getOrDefault("actualCash",   "0").toString());
        BigDecimal expectedCash = new BigDecimal(body.getOrDefault("expectedCash", "0").toString());
        BigDecimal totalSales   = new BigDecimal(body.getOrDefault("totalSales",   "0").toString());
        BigDecimal totalExpenses= new BigDecimal(body.getOrDefault("totalExpenses","0").toString());
        BigDecimal totalProfit  = new BigDecimal(body.getOrDefault("totalProfit",  "0").toString());
        String comment = body.getOrDefault("comment", "").toString();

        if (dc == null) {
            dc = DailyClose.builder()
                    .company(companyService.getById(cid))
                    .closeDate(closeDate)
                    .createdBy(principal.getUserId())
                    .build();
        }

        dc.setExpectedCash(expectedCash);
        dc.setActualCash(actualCash);
        dc.setCashDifference(actualCash.subtract(expectedCash));
        dc.setTotalSales(totalSales);
        dc.setTotalExpenses(totalExpenses);
        dc.setTotalProfit(totalProfit);
        dc.setResponsibleUserId(principal.getUserId());
        dc.setComment(comment);
        dc.setStatus("open");

        return ResponseEntity.ok(dailyCloseRepository.save(dc));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<DailyClose> close(
            @AuthenticationPrincipal BizControlPrincipal principal,
            @PathVariable Long id) {

        permissionService.require(principal, "daily_close.create");
        DailyClose dc = dailyCloseRepository.findByCompanyIdAndId(principal.getCompanyId(), id)
                .orElseThrow(() -> BusinessException.notFound("DailyClose"));

        if ("closed".equals(dc.getStatus())) {
            throw new BusinessException("Already closed");
        }

        dc.setStatus("closed");
        dc.setClosedAt(LocalDateTime.now());
        return ResponseEntity.ok(dailyCloseRepository.save(dc));
    }
}
