package org.gu.fms.desktop.platform.ui.theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Lit le réglage clair/sombre du système d'exploitation.
 * <p>
 * JavaFX 21 n'expose pas cette information (Platform.getPreferences() n'existe qu'à partir de JavaFX 22) ;
 * on lit donc directement le réglage de Windows. En cas d'échec, on considère le thème clair.
 */
public final class SystemTheme {
    public SystemTheme() {
    }

    static boolean isDark() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String out = run("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"
                );
                return out.contains("0x0");
            }
            if (os.contains("mac")) {
                return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase(Locale.ROOT).contains("dark");
            }
            return run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").contains("dark");
        }catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        return out.toString();
    }
}
