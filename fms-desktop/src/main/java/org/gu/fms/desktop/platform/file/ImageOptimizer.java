package org.gu.fms.desktop.platform.file;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Allège les photos jointes (un cliché de téléphone de 4 Mo devient quelques centaines de Ko) :
 * <ul>
 *   <li>JPEG : redressé selon l'orientation EXIF (sinon une photo prise en portrait s'afficherait couchée une fois
 *       les métadonnées retirées), réduit à {@value #MAX_EDGE} pixels sur le plus grand côté, réenregistré en
 *       qualité {@value #JPEG_QUALITY} ;</li>
 *   <li>PNG : réduit seulement s'il dépasse {@value #MAX_EDGE} pixels (format conservé : transparence, texte net) ;</li>
 *   <li>le contenu d'origine est gardé quand le traitement ne gagne rien ou échoue (image illisible, CMJN…).</li>
 * </ul>
 * À {@value #MAX_EDGE} pixels, une page A4 photographiée reste lisible (environ 170 ppp).
 */
public final class ImageOptimizer {

    public static final int MAX_EDGE = 2000;
    public static final float JPEG_QUALITY = 0.85f;

    private ImageOptimizer() {
    }

    public static byte[] optimize(byte[] content, FileKind kind) {
        if (!kind.isImage()) {
            return content;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return content;
            }
            int orientation = kind == FileKind.JPEG ? exifOrientation(content) : 1;
            int w = source.getWidth();
            int h = source.getHeight();
            boolean quarter = orientation >= 5;
            int ow = quarter ? h : w;
            int oh = quarter ? w : h;
            double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(ow, oh));
            if (orientation == 1 && scale == 1.0 && kind == FileKind.PNG) {
                return content;                         // PNG déjà raisonnable : intact
            }
            int tw = Math.max(1, (int) Math.round(ow * scale));
            int th = Math.max(1, (int) Math.round(oh * scale));
            boolean alpha = kind == FileKind.PNG && source.getColorModel().hasAlpha();
            BufferedImage target = new BufferedImage(tw, th, alpha ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (!alpha) {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, tw, th);
                }
                AffineTransform t = AffineTransform.getScaleInstance(scale, scale);
                t.concatenate(orient(orientation, w, h));
                g.drawImage(source, t, null);
            } finally {
                g.dispose();
            }
            byte[] result = kind == FileKind.PNG ? png(target) : jpeg(target);
            // Redressée : toujours gardée (l'orientation EXIF n'existe plus) ; sinon seulement si plus légère
            return orientation != 1 || result.length < content.length ? result : content;
        } catch (IOException | RuntimeException e) {
            return content;
        }
    }

    /** Transformation qui redresse une image {@code w × h} selon l'orientation EXIF (1 à 8). */
    static AffineTransform orient(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);          // miroir horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);         // 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);          // miroir vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);           // transposition
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);          // 90° horaire
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);         // transverse
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);          // 90° antihoraire
            default -> new AffineTransform();
        };
    }

    /** Orientation EXIF d'un JPEG (balise 0x0112 de l'IFD0), 1 si absente ou illisible. */
    static int exifOrientation(byte[] jpeg) {
        int i = 2;                                                     // après FFD8
        while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marker = jpeg[i + 1] & 0xFF;
            int length = u16(jpeg, i + 2, true);
            if (marker == 0xDA || length < 2) {
                return 1;                                              // début des données : pas d'EXIF
            }
            int start = i + 4;
            if (marker == 0xE1 && start + 14 <= jpeg.length && jpeg[start] == 'E' && jpeg[start + 1] == 'x'
                    && jpeg[start + 2] == 'i' && jpeg[start + 3] == 'f') {
                int tiff = start + 6;
                boolean big = jpeg[tiff] == 'M';
                int ifd = tiff + (int) u32(jpeg, tiff + 4, big);
                if (ifd + 2 > jpeg.length) {
                    return 1;
                }
                int count = u16(jpeg, ifd, big);
                for (int k = 0; k < count; k++) {
                    int entry = ifd + 2 + k * 12;
                    if (entry + 12 > jpeg.length) {
                        return 1;
                    }
                    if (u16(jpeg, entry, big) == 0x0112) {
                        int value = u16(jpeg, entry + 8, big);
                        return value >= 1 && value <= 8 ? value : 1;
                    }
                }
                return 1;
            }
            i += 2 + length;
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean bigEndian) {
        int a = b[at] & 0xFF;
        int c = b[at + 1] & 0xFF;
        return bigEndian ? (a << 8) | c : (c << 8) | a;
    }

    private static long u32(byte[] b, int at, boolean bigEndian) {
        long hi = u16(b, bigEndian ? at : at + 2, bigEndian);
        long lo = u16(b, bigEndian ? at + 2 : at, bigEndian);
        return (hi << 16) | lo;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
