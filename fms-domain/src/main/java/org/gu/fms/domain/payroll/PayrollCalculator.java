package org.gu.fms.domain.payroll;

import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.PayScheme;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Calcul d'un bulletin mensuel (instructions §4.7), identique à l'édition Excel.
 *
 * <ol>
 *   <li>Fixe et commission selon l'accord du chauffeur ; commission = versements reçus du mois × taux.</li>
 *   <li>Brut = fixe + commission + ajustement (jamais négatif).</li>
 *   <li>Cotisations en vigueur au 1er du mois, si « Appliquer » = Oui ; CNSS, CAMU et TUS seulement pour un
 *       chauffeur déclaré à la CNSS. Assiette = min(brut, plafond) − plancher.</li>
 *   <li>ITS (si activé et chauffeur assujetti) : barème annuel appliqué au brut × 12 diminué de la retraite
 *       salariale × 12 (voir {@link ItsScale}), puis divisé par 12.</li>
 *   <li>Net avant retenues = brut − cotisations salariales − ITS.</li>
 *   <li>Retenues proposées, dans cet ordre et dans la limite du net : avances, manquants, autres dettes
 *       (manquants + autres dettes plafonnés par la politique). L'utilisateur peut les diminuer.</li>
 *   <li>Coût employeur = brut + charges patronales (c'est lui qui entre dans le résultat).</li>
 * </ol>
 * Arrondi commercial (demi supérieur) au franc pour la commission, chaque cotisation et l'ITS mensuel.
 */
public final class PayrollCalculator {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal MONTHS = BigDecimal.valueOf(12);

    private PayrollCalculator() {
    }

    public static PayrollResult calculate(PayrollInput in) {
        CurrencyUnit currency = in.collectionsBase().currency();
        Money zero = Money.zero(currency);
        Driver d = in.driver();
        LocalDate day = in.period().atDay(1);
        Set<String> warnings = new LinkedHashSet<>();

        PayScheme scheme = d.payScheme();
        Money fixed = scheme != null && scheme.hasFixedPart() && d.fixedSalary() != null ? d.fixedSalary() : zero;
        Money commission = scheme != null && scheme.hasCommission() && d.commissionRate() != null
                ? in.collectionsBase().percent(d.commissionRate().value(), ROUNDING) : zero;
        if (commission.isNegative()) {
            commission = zero;                      // plus d'avoirs que de versements sur le mois
        }
        Money adjustment = in.adjustment() == null ? zero : in.adjustment();
        Money gross = fixed.plus(commission).plus(adjustment);
        if (gross.isNegative()) {
            gross = zero;
            warnings.add("payroll.warning.negativeGross");
        }

        List<PayslipLine> lines = new ArrayList<>();
        Money employee = zero;
        Money pension = zero;
        Money employer = zero;
        for (PayrollContribution c : inForce(in.contributions(), day)) {
            if (!c.enabled() || !appliesTo(c, d)) {
                continue;
            }
            Money base = base(gross, c);
            Money amount = base.percent(c.rate().value(), ROUNDING);
            lines.add(new PayslipLine(c.code(), c.name(), c.group(), c.payer(), base, c.rate(), amount));
            if (c.payer() == ContributionPayer.EMPLOYEE) {
                employee = employee.plus(amount);
                if (c.group() == ContributionGroup.CNSS) {
                    pension = pension.plus(amount);
                }
            } else {
                employer = employer.plus(amount);
            }
        }

        Money its = zero;
        BigDecimal parts = FamilyParts.of(d.maritalStatus(), d.dependentChildren() == null ? 0 : d.dependentChildren());
        if (in.its() != null && in.its().enabled() && d.itsSubject()) {
            List<ItsBand> bands = bandsInForce(in.itsBands(), day);
            if (bands.isEmpty() || !ItsScale.validate(bands).isEmpty()) {
                warnings.add("payroll.warning.itsScaleMissing");
            } else {
                ItsScale scale = ItsScale.of(bands, currency);
                Money taxable = ItsScale.netTaxable(gross.times(12), pension.times(12), in.its().abatement());
                Money annual = scale.annualTax(taxable, parts, in.its().minimumTax());
                its = gross.isZero() ? zero : Money.of(BigDecimal.valueOf(annual.minor())
                        .divide(MONTHS, 0, ROUNDING).longValueExact(), currency);
            }
        }

        Money net = gross.minus(employee).minus(its);
        if (net.isNegative()) {
            net = zero;
            warnings.add("payroll.warning.negativeNet");
        }

        Recoveries max = maxRecoveries(net, in.balance() == null ? null : in.balance(), in.policy(), currency);
        Recoveries applied = in.requested() == null ? max : in.requested();
        Money netPayable = net.minus(applied.total());

        return new PayrollResult(fixed, commission, gross, List.copyOf(lines), employee, its, parts, net, max,
                applied, netPayable, employer, gross.plus(employer), List.copyOf(warnings));
    }

    /**
     * Retenues demandées acceptables ? Chacune ≥ 0 et ≤ son plafond proposé.
     */
    public static List<FieldError> validateRequested(Recoveries requested, Recoveries max) {
        List<FieldError> errors = new ArrayList<>();
        check(errors, "advanceRecovered", requested.advance(), max.advance());
        check(errors, "shortageRecovered", requested.shortage(), max.shortage());
        check(errors, "otherDebtRecovered", requested.otherDebt(), max.otherDebt());
        return errors;
    }

    private static void check(List<FieldError> errors, String field, Money value, Money max) {
        if (value == null) {
            errors.add(FieldError.of(field, "validation.required"));
        } else if (value.isNegative()) {
            errors.add(FieldError.of(field, "validation.notNegative"));
        } else if (value.compareTo(max) > 0) {
            errors.add(FieldError.of(field, "payroll.recovery.aboveMax", max.minor()));
        }
    }

    static Recoveries maxRecoveries(Money net, DriverBalance balance,
                                    RecoveryPolicy policy, CurrencyUnit currency) {
        Money zero = Money.zero(currency);
        if (balance == null || policy == null) {
            return Recoveries.zero(currency);
        }
        Money remaining = net;
        Money advance = policy.recoverAdvances() ? min(balance.advanceDue(), remaining) : zero;
        remaining = remaining.minus(advance);
        Percent cap = policy.debtCap() == null ? Percent.of("100") : policy.debtCap();
        Money debtRoom = min(net.percent(cap.value(), RoundingMode.DOWN), remaining);
        Money shortage = policy.recoverShortages() ? min(balance.shortageDue(), debtRoom) : zero;
        debtRoom = debtRoom.minus(shortage);
        Money other = policy.recoverOtherDebts() ? min(balance.otherDebtDue(), debtRoom) : zero;
        return new Recoveries(advance, shortage, other);
    }

    private static boolean appliesTo(PayrollContribution c, Driver d) {
        return c.group() == ContributionGroup.OTHER || d.cnssSubject();
    }

    private static Money base(Money gross, PayrollContribution c) {
        Money capped = c.monthlyCeiling() != null && gross.compareTo(c.monthlyCeiling()) > 0 ? c.monthlyCeiling() : gross;
        Money base = c.monthlyFloor() == null ? capped : capped.minus(c.monthlyFloor());
        return base.isNegative() ? Money.zero(gross.currency()) : base;
    }

    /** Pour chaque code, la ligne la plus récente en vigueur à cette date. */
    static List<PayrollContribution> inForce(List<PayrollContribution> all, LocalDate day) {
        Set<String> codes = new LinkedHashSet<>();
        all.stream().sorted(Comparator.comparing(PayrollContribution::code)).forEach(c -> codes.add(c.code()));
        List<PayrollContribution> result = new ArrayList<>();
        for (String code : codes) {
            PayrollContributionRules.inForce(all, code, day).ifPresent(result::add);
        }
        return result;
    }

    /** Barème en vigueur : les tranches de la date d'effet la plus récente qui ne dépasse pas {@code day}. */
    public static List<ItsBand> bandsInForce(List<ItsBand> all, LocalDate day) {
        LocalDate effective = all.stream().map(ItsBand::effectiveFrom).filter(d -> !d.isAfter(day))
                .max(Comparator.naturalOrder()).orElse(null);
        if (effective == null) {
            return List.of();
        }
        return all.stream().filter(b -> b.effectiveFrom().equals(effective)).toList();
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
