package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.AuditedCrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.payroll.ItsBand;
import org.gu.fms.domain.payroll.ItsScale;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tranches du barème ITS, regroupées par date d'effet. Chaque modification d'une tranche est contrôlée
 * sur le barème complet de sa date d'effet, sauf la tranche « sans limite » manquante pendant la saisie
 * d'un nouveau barème (le barème est alors signalé incomplet, voir {@link #completeness()}).
 */
@Singleton
public final class ItsBandService extends AuditedCrudService<ItsBand> {

    private final ItsBandRepository bands;

    @Inject
    public ItsBandService(ItsBandRepository repository, TransactionManager tx, AuditLog audit, Clock clock,
                          AppSettings settings) {
        super(repository, tx, audit, clock, settings);
        this.bands = repository;
    }

    @Override
    protected String auditType() {
        return "its_band";
    }

    @Override
    protected ItsBand normalize(ItsBand b) {
        return b;
    }

    @Override
    protected List<FieldError> validate(ItsBand b, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        if (b.effectiveFrom() == null) {
            errors.add(FieldError.of("effectiveFrom", "validation.required"));
        }
        if ((b.rate() == null) == (b.fixedAmount() == null)) {
            errors.add(FieldError.of("rate", "its.band.rateOrFixed"));
        }
        if (b.upTo() != null && !b.upTo().isPositive()) {
            errors.add(FieldError.of("upTo", "validation.positive"));
        }
        if (b.fixedAmount() != null && b.fixedAmount().isNegative()) {
            errors.add(FieldError.of("fixedAmount", "validation.notNegative"));
        }
        return errors;
    }

    @Override
    protected void check(ItsBand b, ItsBand before, List<FieldError> errors) {
        if (b.effectiveFrom() == null) {
            return;
        }
        List<ItsBand> scale = new ArrayList<>();
        for (ItsBand other : bands.findByEffectiveFrom(b.effectiveFrom())) {
            if (!Objects.equals(other.id(), b.id())) {
                if (Objects.equals(other.upTo(), b.upTo())) {
                    errors.add(FieldError.of("upTo", "its.band.duplicate"));
                    return;
                }
                scale.add(other);
            }
        }
        scale.add(b);
        for (FieldError e : ItsScale.validate(scale)) {
            if (!"its.scale.lastOpen".equals(e.messageKey())) {
                errors.add(e);
            }
        }
    }

    @Override
    protected FieldError uniqueViolation(ItsBand b) {
        return FieldError.of("upTo", "its.band.duplicate");
    }

    /** Barèmes incomplets ou incohérents, par date d'effet (vide = tout est cohérent). */
    public List<LocalDate> completeness() {
        List<LocalDate> invalid = new ArrayList<>();
        bands.findAll().stream().map(ItsBand::effectiveFrom).distinct().forEach(day -> {
            if (!ItsScale.validate(bands.findByEffectiveFrom(day)).isEmpty()) {
                invalid.add(day);
            }
        });
        return invalid;
    }

    @Override
    protected Long idOf(ItsBand b) {
        return b.id();
    }

    @Override
    protected long versionOf(ItsBand b) {
        return b.version();
    }

    @Override
    protected ItsBand withIdentity(ItsBand b, long id, long version) {
        return b.withIdentity(id, version);
    }
}
