package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uz.bizcontrol.entity.CashTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long>, JpaSpecificationExecutor<CashTransaction> {

    @Query("SELECT COALESCE(SUM(t.amount),0) FROM CashTransaction t WHERE t.company.id = :companyId AND t.transactionType = :type AND t.paymentSource = :source AND t.status = 'active' AND t.transactionDate BETWEEN :from AND :to")
    BigDecimal sumByTypeAndSourceAndDateRange(Long companyId, String type, String source, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(t) FROM CashTransaction t WHERE t.company.id = :companyId AND t.paymentSource = :source AND t.status = 'active' AND t.transactionDate BETWEEN :from AND :to")
    long countByCompanyIdAndSourceAndDateRange(Long companyId, String source, LocalDateTime from, LocalDateTime to);

    @Query("SELECT t FROM CashTransaction t WHERE t.company.id = :companyId AND t.relatedSaleId = :saleId AND t.status = :status AND t.transactionType = :txType")
    List<CashTransaction> findBySaleIdAndStatusAndType(Long companyId, Long saleId, String status, String txType);

    @Query("SELECT t FROM CashTransaction t WHERE t.company.id = :companyId AND t.relatedPurchaseId = :purchaseId AND t.status = :status AND t.transactionType = :txType")
    List<CashTransaction> findByPurchaseIdAndStatusAndType(Long companyId, Long purchaseId, String status, String txType);

    @Query("SELECT t FROM CashTransaction t WHERE t.company.id = :companyId AND t.relatedSaleId = :saleId ORDER BY t.createdAt DESC")
    List<CashTransaction> findBySaleId(Long companyId, Long saleId);

    @Query("SELECT t FROM CashTransaction t WHERE t.company.id = :companyId AND t.relatedPurchaseId = :purchaseId ORDER BY t.createdAt DESC")
    List<CashTransaction> findByPurchaseId(Long companyId, Long purchaseId);

    /**
     * Per-currency, per-source net balance derived from the immutable active
     * transaction log. Returns rows of [currency, paymentSource, net] where
     * net = Σ(income) − Σ(expense). Never converts between currencies.
     */
    @Query("SELECT t.currency, t.paymentSource, " +
           "COALESCE(SUM(CASE WHEN t.transactionType = 'income' THEN t.amount ELSE t.amount * -1 END), 0) " +
           "FROM CashTransaction t WHERE t.company.id = :companyId AND t.status = 'active' " +
           "GROUP BY t.currency, t.paymentSource")
    List<Object[]> balancesByCurrencyAndSource(Long companyId);
}
