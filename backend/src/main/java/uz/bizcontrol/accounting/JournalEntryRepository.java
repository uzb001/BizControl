package uz.bizcontrol.accounting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findByCompanyIdOrderByEntryDateDescIdDesc(Long companyId, Pageable pageable);

    List<JournalEntry> findByCompanyIdAndSourceTypeAndSourceId(Long companyId, String sourceType, Long sourceId);

    /**
     * Trial-balance aggregation: total debit/credit per account across all POSTED
     * (non-reversed) entries for a company. Returns rows of [accountId, debit, credit].
     */
    @Query("SELECT l.accountId, COALESCE(SUM(l.debit),0), COALESCE(SUM(l.credit),0) " +
           "FROM JournalLine l " +
           "WHERE l.entry.companyId = :cid AND l.entry.status = 'posted' " +
           "GROUP BY l.accountId")
    List<Object[]> trialBalanceRaw(@Param("cid") Long companyId);
}
