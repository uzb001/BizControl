package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.bizcontrol.entity.ExchangeRate;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.ExchangeRateService;
import uz.bizcontrol.service.PermissionService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal BizControlPrincipal p) {
        permissionService.require(p, "dashboard.view");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("base", exchangeRateService.baseCurrency(p.getCompanyId()));
        result.put("rates", exchangeRateService.list(p.getCompanyId()));
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<ExchangeRate> set(@AuthenticationPrincipal BizControlPrincipal p,
                                            @RequestBody Map<String, Object> body) {
        permissionService.require(p, "settings.edit_company");
        Object rate = body.get("rate");
        if (rate == null) throw new BusinessException("Rate is required");
        return ResponseEntity.ok(exchangeRateService.setRate(
                p.getCompanyId(), p.getUserId(),
                body.get("currency") != null ? body.get("currency").toString() : null,
                new BigDecimal(rate.toString())));
    }
}
