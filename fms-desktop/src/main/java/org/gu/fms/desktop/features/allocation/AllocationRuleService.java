package org.gu.fms.desktop.features.allocation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.allocation.AllocationRule;
import org.gu.fms.domain.allocation.AllocationRules;
import org.gu.fms.domain.common.FieldError;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Règles de provisions. Refuse plus de 100 % de provisions actives sur une même base en pourcentage. */
@Singleton
public final class AllocationRuleService extends AuditedCrudService<AllocationRule> {

    @Inject
    public AllocationRuleService(AllocationRuleRepository repository, TransactionManager tx, AuditLog audit,
                                 Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
    }

    @Override
    protected String auditType() {
        return "allocation_rule";
    }

    @Override
    protected AllocationRule normalize(AllocationRule r) {
        return AllocationRules.normalize(r);
    }

    @Override
    protected List<FieldError> validate(AllocationRule r, LocalDate today) {
        return AllocationRules.validate(r);
    }

    @Override
    protected void check(AllocationRule r, AllocationRule before, List<FieldError> errors) {
        if (r.name() != null && repository.existsWithValue("name", r.name(), r.id())) {
            errors.add(FieldError.of("name", "allocation.name.taken", r.name()));
        }
        if (r.basis() != null && r.basis().isPercentage() && r.rate() != null) {
            BigDecimal total = AllocationRules.totalForBasis(r, repository.findAll());
            if (AllocationRules.exceedsHundred(total)) {
                errors.add(FieldError.of("rate", "allocation.totalExceeds", total.stripTrailingZeros().toPlainString()));
            }
        }
    }

    @Override
    protected FieldError uniqueViolation(AllocationRule r) {
        return FieldError.of("name", "allocation.name.taken", r.name());
    }

    @Override
    protected Long idOf(AllocationRule r) {
        return r.id();
    }

    @Override
    protected long versionOf(AllocationRule r) {
        return r.version();
    }

    @Override
    protected AllocationRule withIdentity(AllocationRule r, long id, long version) {
        return r.withIdentity(id, version);
    }
}
