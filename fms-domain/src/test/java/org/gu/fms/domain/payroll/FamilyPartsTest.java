package org.gu.fms.domain.payroll;

import org.gu.fms.domain.driver.MaritalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.gu.fms.domain.driver.MaritalStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tableau de l'art. 116 B du CGI (LF 2026), recopié ligne à ligne. */
class FamilyPartsTest {

    private static void parts(String expected, MaritalStatus status, int children) {
        assertEquals(new BigDecimal(expected), FamilyParts.of(status, children), status + " / " + children);
    }

    @Test
    void article116B() {
        parts("1.0", SINGLE, 0);
        parts("1.0", DIVORCED, 0);
        parts("1.0", WIDOWED, 0);
        parts("2.0", MARRIED, 0);
        parts("2.0", SINGLE, 1);
        parts("2.5", MARRIED, 1);
        parts("2.5", WIDOWED, 1);
        parts("2.5", DIVORCED, 2);
        parts("3.0", MARRIED, 2);
        parts("3.0", SINGLE, 3);
        parts("3.5", MARRIED, 3);
        parts("3.5", SINGLE, 4);
        parts("6.5", MARRIED, 9);
        parts("6.5", MARRIED, 12);      // « jusqu’à un maximum de 6,5 parts »
    }
}
