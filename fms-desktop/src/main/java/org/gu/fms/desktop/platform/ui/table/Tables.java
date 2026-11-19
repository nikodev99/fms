package org.gu.fms.desktop.platform.ui.table;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.format.Formats;

@Singleton
public final class Tables {
    private final Formats formats;

    @Inject
    public Tables(Formats formats) {
        this.formats = formats;
    }

    public <T> DataTable<T> create() {
        return new DataTable<>(formats);
    }
}
