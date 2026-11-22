package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.*;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Comptes chauffeurs (instructions §4.7) : manquants, avances, autres dettes. Les mouvements viennent du point
 * journalier, des régularisations, des avances et de la paie ; les ajustements manuels exigent un libellé.
 */
@Singleton
public final class DriverLedgerService {

    static final String AUDIT_TYPE = "driver_ledger";

    private final DriverLedgerRepository entries;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final UserSession session;
    private final Clock clock;
    private final AppSettings settings;

    @Inject
    public DriverLedgerService(DriverLedgerRepository entries, TransactionManager tx, AuditLog audit,
                               UserSession session, Clock clock, AppSettings settings) {
        this.entries = entries;
        this.tx = tx;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
        this.settings = settings;
    }

    public Map<Long, DriverBalance> balances() {
        return entries.balances();
    }

    public DriverBalance balance(long driverId) {
        return entries.balance(driverId);
    }

    public List<DriverLedgerEntry> movements(long driverId) {
        return entries.findForDriver(driverId);
    }

    /** Mouvement produit par un module (dans la transaction de l'appelant). Un montant nul n'écrit rien. */
    public DriverLedgerEntry post(long driverId, LocalDate date, LedgerRubric rubric, long signedMinor,
                                  LedgerSource source, Long sourceId, String label) {
        if (signedMinor == 0) {
            return null;
        }
        return tx.inTransaction(c -> insert(new DriverLedgerEntry(null, 0, date, clock.instant(), driverId, rubric,
                Money.of(signedMinor, settings.currency()), source, sourceId, label, session.currentUser().displayName())));
    }

    /** Ajustement manuel (dette constatée, remise accordée, correction d'un manquant). */
    public DriverLedgerEntry adjust(DriverLedgerEntry input) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), settings.zone());
        ValidationException.throwIfAny(LedgerRules.validateManual(input, today));
        return tx.inTransaction(c -> insert(new DriverLedgerEntry(null, 0, input.date(), clock.instant(),
                input.driverId(), input.rubric(), input.amount(), LedgerSource.MANUAL, null,
                TextRules.normalize(input.label()), session.currentUser().displayName())));
    }

    /** Annule les mouvements produits par un objet (ex. bulletin annulé) en passant leurs mouvements inverses. */
    public void reverseBySource(LedgerSource source, long sourceId, LocalDate date, String label) {
        tx.runInTransaction(c -> {
            for (DriverLedgerEntry e : entries.findBySource(source, sourceId)) {
                post(e.driverId(), date, e.rubric(), -e.amount().minor(), source, sourceId, label);
            }
        });
    }

    private DriverLedgerEntry insert(DriverLedgerEntry e) {
        if (e.label() == null) {
            throw ValidationException.of(FieldError.of("label", "validation.required"));
        }
        long id = entries.insert(e);
        DriverLedgerEntry saved = e.withIdentity(id, 0);
        audit.created(AUDIT_TYPE, id, entries.snapshot(saved));
        return saved;
    }
}
