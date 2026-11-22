package org.gu.fms.desktop.platform.audit;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/** Encodeur JSON minimal pour le journal d'audit (évite une dépendance pour un seul usage). */
final class Json {

    private Json() {
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            sb.append(value);
        } else if (value instanceof Money m) {
            sb.append(m.minor());
        } else if (value instanceof Percent p) {
            sb.append(p.basisPoints());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                string(sb, String.valueOf(e.getKey()));
                sb.append(':');
                append(sb, e.getValue());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else if (value instanceof Collection<?> list) {
            sb.append('[');
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                append(sb, it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
        } else if (value instanceof Enum<?> e) {
            string(sb, e.name());
        } else {
            string(sb, value.toString());
        }
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}