package org.gu.fms.desktop.platform.ui.overlay;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.i18n.Messages;

@Singleton
public final class Drawers {
    private final OverlayHost host;
    private final Messages messages;

    @Inject
    public Drawers(OverlayHost host, Messages messages) {
        this.host = host;
        this.messages = messages;
    }

    public Drawer create(String title) {
        return new Drawer(host, title, messages.get("action.close"));
    }
}
