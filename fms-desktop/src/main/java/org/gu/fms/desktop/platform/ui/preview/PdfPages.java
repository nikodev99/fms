package org.gu.fms.desktop.platform.ui.preview;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rendu des pages d'un PDF en images (Apache PDFBox), pour l'aperçu. Hors du fil JavaFX. */
public final class PdfPages {

    /** Résolution de rendu : une page A4 fait environ 990 pixels de large. */
    public static final float DPI = 120;
    /** Pages rendues au plus ; au-delà, l'aperçu le signale et le fichier se télécharge. */
    public static final int MAX_PAGES = 40;

    /** @param total nombre de pages du document (peut dépasser {@code pages.size()}) */
    public record Rendered(List<BufferedImage> pages, int total) {
    }

    private PdfPages() {
    }

    public static Rendered render(byte[] pdf, float dpi, int maxPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int total = document.getNumberOfPages();
            int count = Math.min(total, maxPages);
            List<BufferedImage> pages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                pages.add(renderer.renderImageWithDPI(i, dpi, ImageType.RGB));
            }
            return new Rendered(pages, total);
        }
    }

    /** Conversion vers une image JavaFX, sans dépendre du module javafx-swing. */
    public static Image toFx(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);
        WritableImage image = new WritableImage(w, h);
        image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return image;
    }
}
