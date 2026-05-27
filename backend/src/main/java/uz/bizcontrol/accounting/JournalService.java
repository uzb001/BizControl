package uz.bizcontrol.accounting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The double-entry posting engine. Guarantees:
 *  - every entry balances (Σ debit == Σ credit),
 *  - each line is exactly one of debit or credit,
 *  - accounts belong to the posting company,
 *  - entries are immutable: corrections are made via {@link #reverse}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalEntryRepository entryRepository;
    private final AccountRepository accountRepository;

    /** A draft debit line. */
    public static JournalLine debit(Long accountId, BigDecimal amount, String memo) {
        return JournalLine.builder().accountId(accountId).debit(scale(amount)).credit(BigDecimal.ZERO).memo(memo).build();
    }

    /** A draft credit line. */
    public static JournalLine credit(Long accountId, BigDecimal amount, String memo) {
        return JournalLine.builder().accountId(accountId).debit(BigDecimal.ZERO).credit(scale(amount)).memo(memo).build();
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Post a balanced journal entry. Throws {@link BusinessException} on any
     * integrity violation — nothing is persisted in that case.
     */
    @Transactional
    public JournalEntry post(Long companyId, Long userId, LocalDateTime entryDate,
                             String sourceType, Long sourceId, String memo, List<JournalLine> lines) {

        if (lines == null || lines.size() < 2)
            throw new BusinessException("A journal entry needs at least two lines");

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalLine l : lines) {
            BigDecimal d = scale(l.getDebit());
            BigDecimal c = scale(l.getCredit());
            if (d.signum() < 0 || c.signum() < 0)
                throw new BusinessException("Journal line amounts cannot be negative");
            if (d.signum() > 0 && c.signum() > 0)
                throw new BusinessException("A journal line cannot be both debit and credit");
            if (d.signum() == 0 && c.signum() == 0)
                throw new BusinessException("A journal line must have a debit or a credit");
            l.setDebit(d);
            l.setCredit(c);
            totalDebit = totalDebit.add(d);
            totalCredit = totalCredit.add(c);
        }

        if (totalDebit.compareTo(totalCredit) != 0)
            throw new BusinessException("Journal entry is not balanced: debit " + totalDebit + " ≠ credit " + totalCredit);

        // Validate every account belongs to this company.
        Set<Long> companyAccountIds = accountRepository.findByCompanyIdOrderByCode(companyId)
                .stream().map(Account::getId).collect(Collectors.toSet());
        for (JournalLine l : lines) {
            if (!companyAccountIds.contains(l.getAccountId()))
                throw new BusinessException("Account " + l.getAccountId() + " does not belong to company " + companyId);
        }

        JournalEntry entry = JournalEntry.builder()
                .companyId(companyId)
                .entryDate(entryDate != null ? entryDate : LocalDateTime.now())
                .memo(memo)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .status("posted")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .lines(new ArrayList<>())
                .build();

        for (JournalLine l : lines) { l.setEntry(entry); entry.getLines().add(l); }

        entry = entryRepository.save(entry);
        log.info("[Journal] posted entry #{} ({}/{}) balanced at {} for company {}",
                entry.getId(), sourceType, sourceId, totalDebit, companyId);
        return entry;
    }

    /**
     * Reverse an entry by posting its mirror image. The original is never mutated
     * beyond linking + marking 'reversed'.
     */
    @Transactional
    public JournalEntry reverse(Long companyId, Long userId, Long entryId, String reason) {
        JournalEntry orig = entryRepository.findById(entryId)
                .orElseThrow(() -> BusinessException.notFound("JournalEntry"));
        if (!orig.getCompanyId().equals(companyId))
            throw BusinessException.forbidden("Not your company");
        if ("reversed".equals(orig.getStatus()) || orig.getReversedByEntryId() != null)
            throw new BusinessException("Entry #" + entryId + " is already reversed");

        List<JournalLine> mirror = new ArrayList<>();
        for (JournalLine l : orig.getLines()) {
            mirror.add(JournalLine.builder()
                    .accountId(l.getAccountId())
                    .debit(l.getCredit())   // swap
                    .credit(l.getDebit())
                    .memo("Reversal: " + (l.getMemo() == null ? "" : l.getMemo()))
                    .build());
        }

        JournalEntry reversal = post(companyId, userId, LocalDateTime.now(), "REVERSAL", orig.getId(),
                "Reversal of entry #" + orig.getId() + (reason != null ? " — " + reason : ""), mirror);
        reversal.setReversesEntryId(orig.getId());
        entryRepository.save(reversal);

        orig.setStatus("reversed");
        orig.setReversedByEntryId(reversal.getId());
        entryRepository.save(orig);
        return reversal;
    }

    /**
     * Reverse every still-active journal entry produced by a source document
     * (e.g. a cancelled/edited SALE). Idempotent: already-reversed entries are skipped.
     */
    @Transactional
    public void reverseBySource(Long companyId, Long userId, String sourceType, Long sourceId, String reason) {
        for (JournalEntry e : entryRepository.findByCompanyIdAndSourceTypeAndSourceId(companyId, sourceType, sourceId)) {
            if ("posted".equals(e.getStatus()) && e.getReversedByEntryId() == null) {
                reverse(companyId, userId, e.getId(), reason);
            }
        }
    }

    // ── Trial balance ──────────────────────────────────────────────────────────

    public record TrialBalanceRow(Long accountId, String code, String name, String type,
                                  BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    /**
     * Trial balance across all posted entries. `balance` is signed to the account's
     * normal side (positive = normal). Total debit must equal total credit.
     */
    public List<TrialBalanceRow> trialBalance(Long companyId) {
        Map<Long, Account> accounts = accountRepository.findByCompanyIdOrderByCode(companyId)
                .stream().collect(Collectors.toMap(Account::getId, a -> a));
        List<Object[]> raw = entryRepository.trialBalanceRaw(companyId);
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (Object[] r : raw) {
            Long accId = ((Number) r[0]).longValue();
            BigDecimal debit = scale((BigDecimal) r[1]);
            BigDecimal credit = scale((BigDecimal) r[2]);
            Account a = accounts.get(accId);
            if (a == null) continue;
            BigDecimal net = debit.subtract(credit);
            BigDecimal balance = "CREDIT".equals(a.getNormalBalance()) ? net.negate() : net;
            rows.add(new TrialBalanceRow(accId, a.getCode(), a.getName(), a.getType(), debit, credit, balance));
        }
        rows.sort((x, y) -> x.code().compareTo(y.code()));
        return rows;
    }
}
