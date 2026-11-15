package org.gu.fms.desktop.platform.db.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Découpe un script SQL en instructions.
 * <p>
 * Gère : chaînes '…' et identifiants "…" (avec doublement d'échappement), commentaires — et
 * {@code /* *}{@code /}, et les déclencheurs {@code CREATE TRIGGER … BEGIN …; …; END;} dont le corps
 * contient des points-virgules. Dans un déclencheur, CASE… END est aussi suivi pour ne pas
 * confondre son END avec celui du déclencheur.
 */
public final class SqlScriptSplitter {
    private static final Pattern TRIGGER_START =
            Pattern.compile("^CREATE\\s+(TEMP\\s+|TEMPORARY\\s+)?TRIGGER\\b", Pattern.CASE_INSENSITIVE);

    private SqlScriptSplitter() {
    }

    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        StringBuilder code = new StringBuilder();   // texte hors commentaires, pour détecter le type d'instruction
        StringBuilder word = new StringBuilder();
        int depth = 0;
        int i = 0;
        int n = script.length();

        while (i < n) {
            char ch = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : '\0';

            if (ch == '-' && next == '-') {                       // commentaire de ligne
                int end = script.indexOf('\n', i);
                end = end < 0 ? n : end;
                current.append(script, i, end);
                i = end;
                continue;
            }
            if (ch == '/' && next == '*') {                       // commentaire de bloc
                int end = script.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("Commentaire /* non fermé");
                }
                current.append(script, i, end + 2);
                i = end + 2;
                continue;
            }
            if (ch == '\'' || ch == '"') {                        // chaîne ou identifiant
                int end = i + 1;
                while (true) {
                    if (end >= n) {
                        throw new IllegalArgumentException("Chaîne non fermée dans le script SQL");
                    }
                    if (script.charAt(end) == ch) {
                        if (end + 1 < n && script.charAt(end + 1) == ch) {
                            end += 2;                              // '' ou "" : caractère échappé
                            continue;
                        }
                        break;
                    }
                    end++;
                }
                current.append(script, i, end + 1);
                code.append(script, i, end + 1);
                depth = closeWord(word, code, depth);
                i = end + 1;
                continue;
            }

            if (Character.isLetterOrDigit(ch) || ch == '_') {
                word.append(ch);
            } else {
                depth = closeWord(word, code, depth);
            }

            if (ch == ';') {
                boolean inTrigger = TRIGGER_START.matcher(code.toString().strip()).find();
                if (!inTrigger || depth == 0) {
                    addIfNotBlank(statements, current, code);
                    current.setLength(0);
                    code.setLength(0);
                    depth = 0;
                    i++;
                    continue;
                }
            }
            current.append(ch);
            code.append(ch);
            i++;
        }
        depth = closeWord(word, code, depth);
        if (depth != 0) {
            throw new IllegalArgumentException("Bloc BEGIN … END non fermé dans le script SQL");
        }
        addIfNotBlank(statements, current, code);
        return statements;
    }

    /** Un mot vient de se terminer : met à jour la profondeur BEGIN/CASE… END dans un déclencheur. */
    private static int closeWord(StringBuilder word, StringBuilder code, int depth) {
        if (word.isEmpty()) {
            return depth;
        }
        String w = word.toString().toUpperCase(Locale.ROOT);
        word.setLength(0);
        if (!TRIGGER_START.matcher(code.toString().strip()).find()) {
            return depth;
        }
        return switch (w) {
            case "BEGIN", "CASE" -> depth + 1;
            case "END" -> depth - 1;
            default -> depth;
        };
    }

    private static void addIfNotBlank(List<String> statements, StringBuilder current, StringBuilder code) {
        if (!code.toString().isBlank()) {
            statements.add(current.toString().strip());
        }
    }
}
