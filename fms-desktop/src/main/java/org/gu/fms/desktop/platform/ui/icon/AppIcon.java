package org.gu.fms.desktop.platform.ui.icon;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.*;

/**
 * Catalogue des icônes de l'application, par rôle et non par dessin.
 *
 * Les écrans utilisent toujours ces noms ({@code AppIcon.VEHICLE}), jamais un code Ikonli direct :
 * changer de jeu d'icônes ou de dessin se fait ici, une seule fois.
 * Jeu utilisé : Material Design Icons (Ikonli, pack materialdesign2).
 */
public enum AppIcon {
    // Navigation
    DASHBOARD(MaterialDesignV.VIEW_DASHBOARD_OUTLINE),
    CASH_REGISTER(MaterialDesignC.CASH_REGISTER),
    COLLECTIONS(MaterialDesignC.CASH_MULTIPLE),
    TRANSACTIONS(MaterialDesignS.SWAP_HORIZONTAL),
    VEHICLE(MaterialDesignC.CAR_OUTLINE),
    DRIVERS(MaterialDesignA.ACCOUNT_GROUP_OUTLINE),
    MAINTENANCE(MaterialDesignW.WRENCH_OUTLINE),
    ACCOUNTS(MaterialDesignW.WALLET_OUTLINE),
    PAYROLL(MaterialDesignA.ACCOUNT_CASH_OUTLINE),
    LOAN(MaterialDesignB.BANK_OUTLINE),
    REPORTS(MaterialDesignF.FILE_CHART_OUTLINE),
    SIMULATION(MaterialDesignC.CALCULATOR_VARIANT_OUTLINE),
    SETTINGS(MaterialDesignC.COG_OUTLINE),

    // Actions
    ADD(MaterialDesignP.PLUS),
    EDIT(MaterialDesignP.PENCIL_OUTLINE),
    SAVE(MaterialDesignC.CONTENT_SAVE_OUTLINE),
    CLOSE(MaterialDesignC.CLOSE),
    SEARCH(MaterialDesignM.MAGNIFY),
    FILTER(MaterialDesignF.FILTER_VARIANT),
    REFRESH(MaterialDesignR.REFRESH),
    LOGOUT(MaterialDesignL.LOGOUT),
    OPEN_CASH(MaterialDesignL.LOCK_OPEN_VARIANT_OUTLINE),
    CHEVRON_RIGHT(MaterialDesignC.CHEVRON_RIGHT),
    CHEVRON_LEFT(MaterialDesignC.CHEVRON_LEFT),
    REVERSE(MaterialDesignU.UNDO_VARIANT),
    CREDIT_NOTE(MaterialDesignR.RECEIPT_TEXT_MINUS_OUTLINE),
    VALIDATE(MaterialDesignC.CHECK_ALL),
    PAY(MaterialDesignC.CASH_CHECK),
    CANCEL(MaterialDesignC.CANCEL),
    TRANSFER(MaterialDesignB.BANK_TRANSFER),
    LOCK(MaterialDesignL.LOCK_OUTLINE),
    UNLOCK(MaterialDesignL.LOCK_OPEN_OUTLINE),
    CALCULATE(MaterialDesignC.CALCULATOR),
    ADVANCE(MaterialDesignH.HAND_COIN_OUTLINE),
    LEDGER(MaterialDesignB.BOOK_OPEN_PAGE_VARIANT_OUTLINE),
    POINT(MaterialDesignC.CLIPBOARD_CHECK_OUTLINE),
    MENU(MaterialDesignM.MENU),
    MENU_OPEN(MaterialDesignM.MENU_OPEN),
    FOLDER_OPEN(MaterialDesignF.FOLDER_OPEN_OUTLINE),
    BACKUP(MaterialDesignD.DATABASE_EXPORT_OUTLINE),
    RESTORE(MaterialDesignD.DATABASE_IMPORT_OUTLINE),
    IMAGE(MaterialDesignI.IMAGE_OUTLINE),
    REMOVE(MaterialDesignD.DELETE_OUTLINE),

    // Paramètres et sous-sections
    COMPANY(MaterialDesignD.DOMAIN),
    REGIONAL(MaterialDesignE.EARTH),
    APPEARANCE(MaterialDesignP.PALETTE_OUTLINE),
    CATEGORY(MaterialDesignT.TAG_OUTLINE),
    DOCUMENT(MaterialDesignF.FILE_DOCUMENT_OUTLINE),
    DEFAULTS(MaterialDesignT.TUNE),
    CONTRIBUTIONS(MaterialDesignP.PERCENT_OUTLINE),
    TAX(MaterialDesignS.SCALE_BALANCE),
    PROVISIONS(MaterialDesignP.PIGGY_BANK_OUTLINE),
    BACKUP_RESTORE(MaterialDesignB.BACKUP_RESTORE),
    ASSIGNMENT(MaterialDesignA.ACCOUNT_SWITCH_OUTLINE),
    PLAN(MaterialDesignC.CALENDAR_CLOCK),
    THRESHOLD(MaterialDesignG.GAUGE),
    VERIFIED(MaterialDesignC.CHECK_DECAGRAM_OUTLINE),

    // Thème
    THEME_LIGHT(MaterialDesignW.WHITE_BALANCE_SUNNY),
    THEME_DARK(MaterialDesignW.WEATHER_NIGHT),
    THEME_SYSTEM(MaterialDesignM.MONITOR),

    // États
    NOTIFICATIONS(MaterialDesignB.BELL_OUTLINE),
    SUCCESS(MaterialDesignC.CHECK_CIRCLE_OUTLINE),
    INFO(MaterialDesignI.INFORMATION_OUTLINE),
    WARNING(MaterialDesignA.ALERT_OUTLINE),
    ERROR(MaterialDesignA.ALERT_CIRCLE_OUTLINE),
    UNDER_CONSTRUCTION(MaterialDesignH.HAMMER_WRENCH),
    DATABASE(MaterialDesignD.DATABASE_OUTLINE),
    ARCHIVE(MaterialDesignA.ARCHIVE_OUTLINE);

    private final Ikon ikon;

    AppIcon(Ikon ikon) {
        this.ikon = ikon;
    }

    public Ikon ikon() {
        return ikon;
    }
}
