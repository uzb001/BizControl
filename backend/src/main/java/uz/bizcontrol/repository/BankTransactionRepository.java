package uz.bizcontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.bizcontrol.entity.CashTransaction;

/**
 * @deprecated Use {@link CashTransactionRepository} with paymentSource="bank" filter.
 * This interface is kept only to avoid breaking any remaining references.
 * All bank transactions are now stored in money_transactions via CashTransaction.
 */
@Deprecated
public interface BankTransactionRepository
        extends JpaRepository<CashTransaction, Long>, JpaSpecificationExecutor<CashTransaction> {
}
