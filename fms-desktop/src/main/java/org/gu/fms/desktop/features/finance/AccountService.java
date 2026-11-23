package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.finance.AccountRules;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Comptes de trésorerie. Un compte qui a des mouvements garde son solde et sa date d'ouverture ; il ne peut être
 * désactivé que si son solde est nul (sinon de l'argent disparaîtrait de la trésorerie consolidée).
 */
@Singleton
public final class AccountService extends AuditedCrudService<FinancialAccount> {

    private final AccountRepository accounts;
    private final Ledger ledger;

    @Inject
    public AccountService(AccountRepository repository, Ledger ledger, TransactionManager tx, AuditLog audit,
                          Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.accounts = repository;
        this.ledger = ledger;
    }

    @Override
    protected String auditType() {
        return "financial_account";
    }

    @Override
    protected FinancialAccount normalize(FinancialAccount a) {
        return AccountRules.normalize(a);
    }

    @Override
    protected List<FieldError> validate(FinancialAccount a, LocalDate today) {
        return AccountRules.validate(a, today);
    }

    @Override
    protected void check(FinancialAccount a, FinancialAccount before, List<FieldError> errors) {
        if (a.name() != null && accounts.existsWithValue("name", a.name(), a.id())) {
            errors.add(FieldError.of("name", "account.name.taken"));
        }
        if (before == null) {
            return;
        }
        errors.addAll(AccountRules.validateChange(before, a, accounts.hasMovements(before.id())));
        if (before.active() && !a.active()) {
            Money balance = ledger.balance(before.id());
            if (!balance.isZero()) {
                errors.add(FieldError.of("active", "account.deactivateNonZero"));
            }
        }
    }

    @Override
    protected FieldError uniqueViolation(FinancialAccount a) {
        return FieldError.of("name", "account.name.taken");
    }

    @Override
    protected Long idOf(FinancialAccount a) {
        return a.id();
    }

    @Override
    protected long versionOf(FinancialAccount a) {
        return a.version();
    }

    @Override
    protected FinancialAccount withIdentity(FinancialAccount a, long id, long version) {
        return a.withIdentity(id, version);
    }
}
