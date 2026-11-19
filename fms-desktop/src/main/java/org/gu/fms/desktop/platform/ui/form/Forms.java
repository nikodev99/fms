package org.gu.fms.desktop.platform.ui.form;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;

/** Fabrique de formulaires (injectée) : fournit textes et formats à chaque champ. */
@Singleton
public final class Forms {
    private final Messages messages;
    private final Formats formats;

    @Inject
    private Forms(final Messages messages, final Formats formats) {
        this.messages = messages;
        this.formats = formats;
    }

    public Form create() {
        return new Form(messages, formats);
    }
}
