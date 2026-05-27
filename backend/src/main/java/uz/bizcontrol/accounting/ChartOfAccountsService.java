package uz.bizcontrol.accounting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.bizcontrol.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds and resolves a company's chart of accounts. Idempotent: safe to call on
 * every accounting operation so companies created after the V10 migration still
 * get their standard accounts.
 */
@Service
@RequiredArgsConstructor
public class ChartOfAccountsService {

    private final AccountRepository accountRepository;

    /** Create the standard chart for a company if it has none. */
    @Transactional
    public void ensureSeeded(Long companyId) {
        if (accountRepository.existsByCompanyId(companyId)) return;
        List<Account> toCreate = new ArrayList<>();
        for (ChartOfAccounts.Def d : ChartOfAccounts.TEMPLATE) {
            toCreate.add(Account.builder()
                    .companyId(companyId)
                    .code(d.code())
                    .name(d.name())
                    .type(d.type())
                    .normalBalance(d.normalBalance())
                    .isSystem(true)
                    .build());
        }
        accountRepository.saveAll(toCreate);
    }

    /** Resolve an account by canonical code, seeding the chart first if needed. */
    @Transactional
    public Account require(Long companyId, String code) {
        ensureSeeded(companyId);
        return accountRepository.findByCompanyIdAndCode(companyId, code)
                .orElseThrow(() -> new BusinessException("Account " + code + " not found for company " + companyId));
    }

    /**
     * Resolve an account by code, creating that single account if it is missing.
     * Used for accounts added after a company's chart was first seeded (e.g. the
     * production accounts), so older companies pick them up without re-seeding.
     */
    @Transactional
    public Account ensureAccount(Long companyId, String code, String name, String type, String normalBalance) {
        ensureSeeded(companyId);
        return accountRepository.findByCompanyIdAndCode(companyId, code)
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .companyId(companyId).code(code).name(name).type(type)
                        .normalBalance(normalBalance).isSystem(true).build()));
    }

    public List<Account> list(Long companyId) {
        ensureSeeded(companyId);
        return accountRepository.findByCompanyIdOrderByCode(companyId);
    }
}
