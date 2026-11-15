package org.gu.fms.desktop.platform.db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Liste les fichiers d'un dossier du classpath, que l'application tourne depuis l'IDE (dossier) ou un jar. */
public final class ClasspathResources {
    public ClasspathResources() {
    }

    static List<String> list(ClassLoader loader, String directory, String suffix) {
        String dir = directory.endsWith("/") ? directory:  directory + "/";
        TreeSet<String> names = new TreeSet<>();
        try {
            Enumeration<URL> roots = loader.getResources(dir);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                switch (url.getProtocol()) {
                    case "file" -> listDirectory(url, dir, suffix, names);
                    case "jar" -> listJar(url, dir, suffix, names);
                    default -> throw new MigrationException("Emplacement de migrations non pris en charge : " + url);
                }
            }
        }catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ArrayList<>(names);
    }

    static String read(ClassLoader loader, String resource) {
        try(InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new MigrationException("Ressource introuvable : " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void listJar(URL url, String dir, String suffix, TreeSet<String> names) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(dir) && name.endsWith(suffix) && name.indexOf('/', dir.length()) < 0) {
                    names.add(name);
                }
            }
        }
    }

    private static void listDirectory(URL url, String dir, String suffix, TreeSet<String> names) throws IOException {
        Path path;
        try {
            path = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        try (Stream<Path> files = Files.list(path)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .forEach(name -> names.add(dir + name));
        }
    }
}
