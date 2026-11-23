package org.gu.fms.desktop.features.finance;

import javafx.scene.control.ListCell;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionDirection;

/** Présentation commune des sens et états de transactions (badges, listes). */
public final class Tones {

    private Tones() {
    }

    public static Badge direction(Messages messages, TransactionDirection d) {
        return new Badge(messages.label(d), d == TransactionDirection.INFLOW ? Tone.SUCCESS : Tone.WARNING);
    }

    public static Badge state(Messages messages, FinancialTransaction t) {
        if (t.isReversal()) {
            return new Badge(messages.get("transaction.state.reversal"), Tone.NEUTRAL);
        }
        if (t.isReversed()) {
            return new Badge(messages.get("transaction.state.reversed"), Tone.DANGER);
        }
        return new Badge(messages.get("transaction.state.valid"), Tone.SUCCESS);
    }

    /** Cellule de liste pour une énumération, avec un libellé pour « aucun ». */
    public static <E extends Enum<E>> ListCell<E> cell(Messages messages, String noneLabel) {
        return new ListCell<>() {
            @Override
            protected void updateItem(E item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? noneLabel : messages.label(item));
            }
        };
    }
}
