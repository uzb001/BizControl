package uz.bizcontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.bizcontrol.entity.Sale;
import uz.bizcontrol.exception.BusinessException;
import uz.bizcontrol.repository.SaleRepository;
import uz.bizcontrol.repository.*;
import uz.bizcontrol.service.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyIsolationTest {

    @Mock SaleRepository saleRepository;
    @Mock SaleItemRepository saleItemRepository;
    @Mock ProductRepository productRepository;
    @Mock CustomerRepository customerRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock DebtRepository debtRepository;
    @Mock CashTransactionRepository cashTransactionRepository;
    @Mock CompanyService companyService;
    @Mock AuditService auditService;
    @Mock AlertService alertService;
    @Mock ApprovalService approvalService;
    @Mock DailyCloseRepository dailyCloseRepository;
    @Mock uz.bizcontrol.accounting.AccountingService accountingService;

    @InjectMocks SaleService saleService;

    @Test
    void getOne_wrongCompany_throwsNotFound() {
        // Sale belongs to company 2, but we request with company 1
        when(saleRepository.findByCompanyIdAndId(1L, 42L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> saleService.getOne(1L, 42L));
    }

    @Test
    void cancel_wrongCompany_throwsNotFound() {
        when(saleRepository.findByCompanyIdAndId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> saleService.cancel(99L, 1L, 1L));
    }
}
