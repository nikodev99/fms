package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.payroll.PayrollContribution;
import org.gu.fms.domain.payroll.PayrollContributionRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Cotisations et taxes sur salaires. Le code d'une ligne est figé après création (la paie s'y réfère) ;
 * un nouveau taux s'enregistre comme une nouvelle ligne datée.
 */
@Singleton
public final class PayrollContributionService extends AuditedCrudService<PayrollContribution> {

    private final PayrollContributionRepository contributions;

    @Inject
    public PayrollContributionService(PayrollContributionRepository repository, TransactionManager tx, AuditLog audit,
                                      Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.contributions = repository;
    }

    @Override
    protected String auditType() {
        return "payroll_contribution";
    }

    @Override
    protected PayrollContribution normalize(PayrollContribution c) {
        return PayrollContributionRules.normalize(c);
    }

    @Override
    protected List<FieldError> validate(PayrollContribution c, LocalDate today) {
        return PayrollContributionRules.validate(c);
    }

    @Override
    protected void check(PayrollContribution c, PayrollContribution before, List<FieldError> errors) {
        if (before != null && !Objects.equals(before.code(), c.code())) {
            errors.add(FieldError.of("code", "contribution.code.fixed"));
        }
        if (c.code() != null && c.effectiveFrom() != null
                && contributions.versionExists(c.code(), c.effectiveFrom(), c.id())) {
            errors.add(FieldError.of("effectiveFrom", "contribution.versionExists"));
        }
    }

    @Override
    protected FieldError uniqueViolation(PayrollContribution c) {
        return FieldError.of("effectiveFrom", "contribution.versionExists");
    }

    @Override
    protected Long idOf(PayrollContribution c) {
        return c.id();
    }

    @Override
    protected long versionOf(PayrollContribution c) {
        return c.version();
    }

    @Override
    protected PayrollContribution withIdentity(PayrollContribution c, long id, long version) {
        return c.withIdentity(id, version);
    }
}
