package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.driver.PayScheme;
import org.gu.fms.domain.payroll.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bulletins de paie et leurs lignes de cotisations (lignes remplacées seulement tant que le bulletin est brouillon). */
@Singleton
public final class PayslipRepository extends JdbcRepository<Payslip> {

    private final AppSettings settings;

    @Inject
    public PayslipRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "payslip";
    }

    @Override
    protected String defaultOrder() {
        return "period DESC, id";
    }

    @Override
    protected RowMapper<Payslip> mapper() {
        var c = settings.currency();
        return row -> new Payslip(row.longValue("id"), row.longValue("version"), YearMonth.parse(row.string("period")),
                row.longValue("driver_id"), row.enumValue("pay_scheme", PayScheme.class),
                row.money("collections_base", c), row.money("fixed_part", c), row.money("commission", c),
                row.money("adjustment", c), row.string("adjustment_reason"), row.money("gross", c),
                row.money("employee_contributions", c), row.money("its", c),
                BigDecimal.valueOf(row.longValue("its_parts_tenths"), 1), row.money("net_before_recoveries", c),
                row.money("advance_recovered", c), row.money("shortage_recovered", c),
                row.money("other_debt_recovered", c), row.string("recovery_note"), row.money("net_payable", c),
                row.money("employer_charges", c), row.money("employer_cost", c),
                row.enumValue("status", PayslipStatus.class), row.string("validated_by"), row.instant("validated_at"),
                row.localDate("paid_date"), row.longOrNull("account_id"), row.string("payment_reference"),
                row.longOrNull("transaction_id"), row.string("cancel_reason"), List.of());
    }

    @Override
    protected Long idOf(Payslip p) {
        return p.id();
    }

    @Override
    protected long versionOf(Payslip p) {
        return p.version();
    }

    @Override
    protected Map<String, Object> columnsOf(Payslip p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", p.period().toString());
        m.put("driver_id", p.driverId());
        m.put("pay_scheme", p.payScheme());
        m.put("collections_base", p.collectionsBase());
        m.put("fixed_part", p.fixedPart());
        m.put("commission", p.commission());
        m.put("adjustment", p.adjustment());
        m.put("adjustment_reason", p.adjustmentReason());
        m.put("gross", p.gross());
        m.put("employee_contributions", p.employeeContributions());
        m.put("its", p.its());
        m.put("its_parts_tenths", p.itsParts().movePointRight(1).longValueExact());
        m.put("net_before_recoveries", p.netBeforeRecoveries());
        m.put("advance_recovered", p.advanceRecovered());
        m.put("shortage_recovered", p.shortageRecovered());
        m.put("other_debt_recovered", p.otherDebtRecovered());
        m.put("recovery_note", p.recoveryNote());
        m.put("net_payable", p.netPayable());
        m.put("employer_charges", p.employerCharges());
        m.put("employer_cost", p.employerCost());
        m.put("status", p.status());
        m.put("validated_by", p.validatedBy());
        m.put("validated_at", p.validatedAt());
        m.put("paid_date", p.paidDate());
        m.put("account_id", p.accountId());
        m.put("payment_reference", p.paymentReference());
        m.put("transaction_id", p.transactionId());
        m.put("cancel_reason", p.cancelReason());
        return m;
    }

    public List<Payslip> findForPeriod(YearMonth period) {
        return jdbc.query("SELECT * FROM payslip WHERE period = ? ORDER BY id", mapper(), period.toString());
    }

    /** Bulletins des mois [from, to] (sans leurs lignes). */
    public List<Payslip> findBetween(YearMonth from, YearMonth to) {
        return jdbc.query("SELECT * FROM payslip WHERE period BETWEEN ? AND ? ORDER BY period, id", mapper(),
                from.toString(), to.toString());
    }

    public Optional<Payslip> findActive(YearMonth period, long driverId) {
        return jdbc.queryOne("SELECT * FROM payslip WHERE period = ? AND driver_id = ? AND status <> 'CANCELLED'",
                mapper(), period.toString(), driverId);
    }

    /** Bulletin avec ses lignes de cotisations. */
    public Optional<Payslip> findWithLines(long id) {
        return findById(id).map(p -> new Payslip(p.id(), p.version(), p.period(), p.driverId(), p.payScheme(),
                p.collectionsBase(), p.fixedPart(), p.commission(), p.adjustment(), p.adjustmentReason(), p.gross(),
                p.employeeContributions(), p.its(), p.itsParts(), p.netBeforeRecoveries(), p.advanceRecovered(),
                p.shortageRecovered(), p.otherDebtRecovered(), p.recoveryNote(), p.netPayable(), p.employerCharges(),
                p.employerCost(), p.status(), p.validatedBy(), p.validatedAt(), p.paidDate(), p.accountId(),
                p.paymentReference(), p.transactionId(), p.cancelReason(), lines(id)));
    }

    public List<PayslipLine> lines(long payslipId) {
        var c = settings.currency();
        return jdbc.query("SELECT * FROM payslip_line WHERE payslip_id = ? ORDER BY position",
                row -> new PayslipLine(row.string("code"), row.string("name"),
                        row.enumValue("contribution_group", ContributionGroup.class),
                        row.enumValue("payer", ContributionPayer.class), row.money("base", c), row.percent("rate_bp"),
                        row.money("amount", c)), payslipId);
    }

    /** Remplace les lignes d'un bulletin brouillon (refusé par la base sinon). */
    public void replaceLines(long payslipId, List<PayslipLine> lines) {
        jdbc.update("DELETE FROM payslip_line WHERE payslip_id = ?", payslipId);
        int position = 0;
        for (PayslipLine l : lines) {
            jdbc.update("INSERT INTO payslip_line (payslip_id, position, code, name, contribution_group, payer, base,"
                            + " rate_bp, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    payslipId, position++, l.code(), l.name(), l.group(), l.payer(), l.base(), l.rate(), l.amount());
        }
    }
}