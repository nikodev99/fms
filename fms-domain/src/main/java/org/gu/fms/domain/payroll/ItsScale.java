package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Barème de l'impôt sur les traitements et salaires (ITS) — CGI art. 116 et 116 A à 116 G
 * (loi n° 42-2025, loi de finances 2026). Calcul vérifié sur le texte publié au Journal officiel :
 * <ol>
 *   <li>salaire brut annuel imposable − part salariale de la cotisation retraite (art. 116) ;</li>
 *   <li>− abattement de 20 % (art. 116) ;</li>
 *   <li>arrondi au millier de francs inférieur, divisé par le nombre de parts (art. 116 A) ;</li>
 *   <li>barème appliqué au revenu d'une part, puis multiplié par le nombre de parts (art. 116 G, 1) ;</li>
 *   <li>impôt annuel minimum 1 200 F CFA (art. 116 G, 2).</li>
 * </ol>
 * Hypothèse NON vérifiée (le texte ne la précise pas) : l'impôt final est arrondi au franc inférieur.
 * Les taux, tranches, abattement et minimum ne sont pas codés ici : ils viennent des paramètres.
 */
public final class ItsScale {

    private final List<ItsBand> bands;
    private final CurrencyUnit currency;

    private ItsScale(List<ItsBand> bands, CurrencyUnit currency) {
        this.bands = bands;
        this.currency = currency;
    }

    /** Construit un barème valide ; refuse un barème incohérent (voir {@link #validate(List)}). */
    public static ItsScale of(List<ItsBand> bands, CurrencyUnit currency) {
        List<FieldError> errors = validate(bands);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Barème ITS incohérent : " + errors);
        }
        return new ItsScale(sorted(bands), currency);
    }

    /**
     * Cohérence : au moins une tranche ; une seule tranche sans limite, la dernière ; limites strictement
     * croissantes ; chaque tranche a soit un taux, soit un forfait.
     */
    public static List<FieldError> validate(List<ItsBand> bands) {
        List<FieldError> errors = new ArrayList<>();
        if (bands.isEmpty()) {
            errors.add(FieldError.of(null, "its.scale.empty"));
            return errors;
        }
        List<ItsBand> list = sorted(bands);
        long open = list.stream().filter(b -> b.upTo() == null).count();
        if (open != 1 || list.get(list.size() - 1).upTo() != null) {
            errors.add(FieldError.of("upTo", "its.scale.lastOpen"));
        }
        Money previous = null;
        for (ItsBand b : list) {
            if ((b.rate() == null) == (b.fixedAmount() == null)) {
                errors.add(FieldError.of("rate", "its.band.rateOrFixed"));
            }
            if (b.upTo() != null) {
                if (!b.upTo().isPositive() || (previous != null && b.upTo().compareTo(previous) <= 0)) {
                    errors.add(FieldError.of("upTo", "its.scale.increasing"));
                }
                previous = b.upTo();
            }
        }
        return errors;
    }

    public List<ItsBand> bands() {
        return bands;
    }

    /** Revenu net imposable annuel (étapes 1 à 3, avant division par les parts). */
    public static Money netTaxable(Money annualGross, Money annualEmployeePension, Percent abatement) {
        Money afterPension = annualGross.minus(annualEmployeePension);
        if (!afterPension.isPositive()) {
            return Money.zero(annualGross.currency());
        }
        BigDecimal abated = BigDecimal.valueOf(afterPension.minor())
                .multiply(BigDecimal.ONE.subtract(abatement.asFraction()));
        long thousands = abated.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).longValueExact();
        return Money.of(thousands * 1000, annualGross.currency());
    }

    /** Impôt annuel (étapes 4 et 5). */
    public Money annualTax(Money netTaxable, BigDecimal parts, Money minimumTax) {
        Objects.requireNonNull(parts, "parts");
        if (parts.signum() <= 0) {
            throw new IllegalArgumentException("Nombre de parts invalide : " + parts);
        }
        BigDecimal perPart = BigDecimal.valueOf(netTaxable.minor()).divide(parts, 10, RoundingMode.DOWN);
        BigDecimal tax = taxPerPart(perPart).multiply(parts);
        long rounded = tax.setScale(0, RoundingMode.DOWN).longValueExact();
        long minimum = minimumTax == null ? 0 : minimumTax.minor();
        return Money.of(Math.max(rounded, minimum), currency);
    }

    BigDecimal taxPerPart(BigDecimal income) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (ItsBand b : bands) {
            BigDecimal upper = b.upTo() == null ? null : BigDecimal.valueOf(b.upTo().minor());
            BigDecimal top = upper == null || income.compareTo(upper) < 0 ? income : upper;
            BigDecimal fraction = top.subtract(lower);
            if (fraction.signum() > 0) {
                tax = tax.add(b.fixedAmount() != null
                        ? BigDecimal.valueOf(b.fixedAmount().minor())
                        : fraction.multiply(b.rate().asFraction()));
            }
            if (upper == null || income.compareTo(upper) <= 0) {
                break;
            }
            lower = upper;
        }
        return tax;
    }

    private static List<ItsBand> sorted(List<ItsBand> bands) {
        List<ItsBand> list = new ArrayList<>(bands);
        list.sort(Comparator.comparing(ItsBand::upTo, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(list);
    }
}
