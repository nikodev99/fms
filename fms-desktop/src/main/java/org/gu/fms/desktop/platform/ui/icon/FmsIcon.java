package org.gu.fms.desktop.platform.ui.icon;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Icône FMS (police Ikonli). La couleur vient de la CSS ({@code -fx-icon-color}) et suit donc le thème.
 *
 * <pre>{@code <FmsIcon icon="VEHICLE" size="18"/>}</pre>
 */
public final class FmsIcon extends FontIcon {
    public static final int DEFAULT_SIZE = 18;

    private final ObjectProperty<AppIcon> icon = new SimpleObjectProperty<>();

    public FmsIcon() {
        getStyleClass().add("fms-icon");
        setIconSize(DEFAULT_SIZE);
        icon.addListener((obs, oldVal, newVal) -> setIconCode(newVal == null ? null : newVal.ikon()));
    }

    public FmsIcon(AppIcon icon) {
        this();
        setIcon(icon);
    }

    public FmsIcon(AppIcon icon, int size) {
        this(icon);
        setIconSize(size);
    }

    public AppIcon getIcon() {
        return icon.get();
    }

    public void setIcon(AppIcon newIcon) {
        icon.set(newIcon);
    }

    public ObjectProperty<AppIcon> iconProperty() {
        return icon;
    }

    /** Alias FXML de {@code isonSize} : {@code size="16"} */
    public int getSize() {
        return getIconSize();
    }

    public void setSize(int newSize) {
        setIconSize(newSize);
    }
}
