package org.gu.fms.desktop.platform.ui.table;

import org.gu.fms.desktop.platform.ui.Tone;

import java.util.Objects;

public record Badge(String text, Tone tone) {
    public Badge {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(tone, "tone");
    }
}
