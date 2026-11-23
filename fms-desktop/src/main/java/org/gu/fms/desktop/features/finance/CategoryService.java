package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.finance.CategoryRules;
import org.gu.fms.domain.finance.TransactionCategory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Catégories de transactions : les catégories créées par l'utilisateur ne sont jamais des catégories système. */
@Singleton
public final class CategoryService extends AuditedCrudService<TransactionCategory> {

    private final CategoryRepository categories;

    @Inject
    public CategoryService(CategoryRepository repository, TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.categories = repository;
    }

    @Override
    protected String auditType() {
        return "transaction_category";
    }

    @Override
    protected TransactionCategory normalize(TransactionCategory c) {
        return CategoryRules.normalize(c);
    }

    @Override
    protected List<FieldError> validate(TransactionCategory c, LocalDate today) {
        return CategoryRules.validate(c);
    }

    @Override
    protected void check(TransactionCategory c, TransactionCategory before, List<FieldError> errors) {
        if (c.name() != null && categories.existsWithValue("name", c.name(), c.id())) {
            errors.add(FieldError.of("name", "txCategory.name.taken"));
        }
        if (before == null) {
            if (c.system() || c.code() != null) {
                errors.add(FieldError.of(null, "txCategory.systemReserved"));
            }
        } else {
            errors.addAll(CategoryRules.validateChange(before, c, categories.isUsed(before.id())));
        }
    }

    @Override
    protected FieldError uniqueViolation(TransactionCategory c) {
        return FieldError.of("name", "txCategory.name.taken");
    }

    @Override
    protected Long idOf(TransactionCategory c) {
        return c.id();
    }

    @Override
    protected long versionOf(TransactionCategory c) {
        return c.version();
    }

    @Override
    protected TransactionCategory withIdentity(TransactionCategory c, long id, long version) {
        return c.withIdentity(id, version);
    }
}
