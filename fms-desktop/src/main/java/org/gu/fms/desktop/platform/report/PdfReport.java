package org.gu.fms.desktop.platform.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.gu.fms.desktop.platform.brand.BrandAssets;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Mise en page commune des rapports PDF (instructions §6.6) : en-tête (entreprise, logo, titre, période, référence,
 * date de génération, auteur), pied de page « page n / total », sections, tableaux, totaux. Sans logo d'entreprise,
 * l'en-tête porte le logo FMS livré avec l'application ({@link BrandAssets}) ; s'il manque, un monogramme dessiné.
 *
 * Polices standard PDF (Helvetica, codage Windows-1252) : accents français couverts ; les espaces insécables fines
 * des montants formatés sont remplacées par des espaces normales.
 */
public final class PdfReport implements AutoCloseable {

    public static final Color INK = new Color(17, 24, 39);
    public static final Color MUTED = new Color(104, 115, 133);
    public static final Color LINE = new Color(227, 231, 237);
    public static final Color HEAD_BG = new Color(241, 244, 248);
    public static final Color ACCENT = new Color(29, 91, 184);
    public static final Color SUCCESS = new Color(46, 157, 106);
    public static final Color WARNING = new Color(242, 179, 61);
    public static final Color DANGER = new Color(214, 69, 58);
    public static final Color EXEMPT = new Color(135, 150, 171);
    public static final Color REST = new Color(223, 227, 233);
    public static final Color PENDING = new Color(233, 240, 251);

    /**
     * @param company        nom de l'entreprise
     * @param companyDetails forme juridique, RCCM, NIU, adresse… (une ligne), ou null
     * @param logo           contenu de l'image du logo (PNG, JPEG), ou null (logo FMS)
     * @param subtitle       période ou objet du rapport
     * @param generated      « Généré le … par … »
     */
    public record Header(String company, String companyDetails, byte[] logo, String title, String subtitle,
                         String reference, String generated) {
    }

    /** Alignement d'une colonne. */
    public enum Align { LEFT, RIGHT, CENTER }

    private final Document document;
    private final PdfWriter writer;
    private final OutputStream out;
    private final Font body;
    private final Font bodyBold;
    private final Font small;
    private final Font smallMuted;
    private final Font section;
    private final Font title;
    private final Font headerCell;

    private PdfReport(OutputStream target, boolean landscape, Header header) throws IOException {
        Rectangle size = landscape ? PageSize.A4.rotate() : PageSize.A4;
        document = new Document(size, 36, 36, 36, 46);
        out = target;
        try {
            BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            body = new Font(regular, 9, Font.NORMAL, INK);
            bodyBold = new Font(bold, 9, Font.NORMAL, INK);
            small = new Font(regular, 7.5f, Font.NORMAL, INK);
            smallMuted = new Font(regular, 7.5f, Font.NORMAL, MUTED);
            section = new Font(bold, 11, Font.NORMAL, ACCENT);
            title = new Font(bold, 16, Font.NORMAL, INK);
            headerCell = new Font(bold, 8, Font.NORMAL, MUTED);
            writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer(regular, header));
            document.addTitle(clean(header.title()));
            document.addCreator("FMS — Gestion de flotte");
            document.open();
            writeHeader(header);
        } catch (DocumentException e) {
            out.close();
            throw new IOException("PDF impossible : " + e.getMessage(), e);
        }
    }

    /** Le PDF s'écrit dans {@code target}, fermé avec le rapport. */
    public static PdfReport create(OutputStream target, boolean landscape, Header header) throws IOException {
        return new PdfReport(target, landscape, header);
    }

    /** Texte compatible avec les polices standard (espaces insécables → espaces). */
    public static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace(' ', ' ').replace(' ', ' ').replace(" ", " ");
    }

    private void writeHeader(Header h) throws DocumentException {
        PdfPTable top = new PdfPTable(new float[] {1, 5, 3});
        top.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        Image img = image(h.logo());                                   // logo de l'entreprise
        if (img == null) {
            img = image(BrandAssets.logo(256).orElse(null));          // logo FMS livré
        }
        if (img == null) {
            img = fmsMark();                                          // monogramme dessiné
        }
        img.scaleToFit(48, 48);
        logoCell.addElement(img);
        top.addCell(logoCell);
        PdfPCell company = new PdfPCell();
        company.setBorder(Rectangle.NO_BORDER);
        company.addElement(new Paragraph(clean(h.company()), bodyBold));
        if (h.companyDetails() != null && !h.companyDetails().isBlank()) {
            company.addElement(new Paragraph(clean(h.companyDetails()), smallMuted));
        }
        top.addCell(company);
        PdfPCell ref = new PdfPCell();
        ref.setBorder(Rectangle.NO_BORDER);
        ref.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph r1 = new Paragraph(clean(h.reference()), small);
        r1.setAlignment(Element.ALIGN_RIGHT);
        Paragraph r2 = new Paragraph(clean(h.generated()), smallMuted);
        r2.setAlignment(Element.ALIGN_RIGHT);
        ref.addElement(r1);
        ref.addElement(r2);
        top.addCell(ref);
        document.add(top);

        Paragraph t = new Paragraph(clean(h.title()), title);
        t.setSpacingBefore(10);
        document.add(t);
        if (h.subtitle() != null) {
            Paragraph s = new Paragraph(clean(h.subtitle()), new Font(body.getBaseFont(), 10, Font.NORMAL, MUTED));
            s.setSpacingAfter(6);
            document.add(s);
        }
        rule();
    }

    private static Image image(byte[] content) {
        if (content == null) {
            return null;
        }
        try {
            return Image.getInstance(content);
        } catch (IOException | RuntimeException e) {
            return null;            // image illisible : repli suivant
        }
    }

    /** Monogramme FMS (si l'image du logo n'est pas livrée) : carré arrondi bleu, « FMS » en blanc. */
    private Image fmsMark() throws DocumentException {
        PdfTemplate t = writer.getDirectContent().createTemplate(48, 48);
        t.setColorFill(ACCENT);
        t.roundRectangle(0, 0, 48, 48, 9);
        t.fill();
        BaseFont bold = bodyBold.getBaseFont();
        float size = 15;
        float width = bold.getWidthPoint("FMS", size);
        t.beginText();
        t.setFontAndSize(bold, size);
        t.setColorFill(Color.WHITE);
        t.setTextMatrix((48 - width) / 2, 18.5f);
        t.showText("FMS");
        t.endText();
        return Image.getInstance(t);
    }

    private void rule() throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" ", small));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setFixedHeight(4);
        line.addCell(c);
        line.setSpacingAfter(8);
        document.add(line);
    }

    public void section(String text) {
        Paragraph p = new Paragraph(clean(text), section);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        add(p);
    }

    public void paragraph(String text) {
        Paragraph p = new Paragraph(clean(text), body);
        p.setSpacingAfter(4);
        add(p);
    }

    public void note(String text) {
        Paragraph p = new Paragraph(clean(text), smallMuted);
        p.setSpacingBefore(2);
        p.setSpacingAfter(4);
        add(p);
    }

    /** Liste « libellé : valeur » sur deux paires par ligne. */
    public void details(List<String[]> pairs) {
        PdfPTable t = new PdfPTable(new float[] {2.2f, 3, 2.2f, 3});
        t.setWidthPercentage(100);
        for (String[] pair : pairs) {
            t.addCell(plain(pair[0], smallMuted, Align.LEFT));
            t.addCell(plain(pair[1], body, Align.LEFT));
        }
        if (pairs.size() % 2 == 1) {
            t.addCell(plain("", body, Align.LEFT));
            t.addCell(plain("", body, Align.LEFT));
        }
        t.setSpacingAfter(4);
        add(t);
    }

    /** Bandeau de chiffres clés (libellé au-dessus, valeur en gras). */
    public void figures(List<String[]> figures) {
        PdfPTable t = new PdfPTable(figures.size());
        t.setWidthPercentage(100);
        for (String[] f : figures) {
            PdfPCell c = new PdfPCell();
            c.setBorderColor(LINE);
            c.setPadding(6);
            c.addElement(new Paragraph(clean(f[0]), smallMuted));
            c.addElement(new Paragraph(clean(f[1]), new Font(bodyBold.getBaseFont(), 11, Font.NORMAL, INK)));
            if (f.length > 2 && f[2] != null) {
                c.addElement(new Paragraph(clean(f[2]), smallMuted));
            }
            t.addCell(c);
        }
        t.setSpacingAfter(6);
        add(t);
    }

    /** Début d'un tableau ; ajouter les lignes avec {@link Table#row}. */
    public Table table(float[] widths, String[] headers, Align[] aligns) {
        return new Table(widths, headers, aligns);
    }

    public final class Table {
        private final PdfPTable table;
        private final Align[] aligns;
        private int rows;

        private Table(float[] widths, String[] headers, Align[] aligns) {
            this.aligns = aligns;
            table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (int i = 0; i < headers.length; i++) {
                PdfPCell c = plain(headers[i], headerCell, aligns[i]);
                c.setBackgroundColor(HEAD_BG);
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColor(LINE);
                c.setPaddingTop(4);
                c.setPaddingBottom(4);
                table.addCell(c);
            }
        }

        public Table row(String... values) {
            for (int i = 0; i < values.length; i++) {
                PdfPCell c = plain(values[i], body, aligns[i]);
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColor(LINE);
                table.addCell(c);
            }
            rows++;
            return this;
        }

        /** Ligne de total (gras, fond clair). */
        public Table total(String... values) {
            for (int i = 0; i < values.length; i++) {
                PdfPCell c = plain(values[i], bodyBold, aligns[i]);
                c.setBackgroundColor(HEAD_BG);
                c.setBorder(Rectangle.NO_BORDER);
                table.addCell(c);
            }
            return this;
        }

        /** Cellule colorée (calendrier) : texte blanc sur fond {@code color}, ou foncé si {@code darkText}. */
        public Table colored(String value, Color color, boolean darkText) {
            Font f = new Font(small.getBaseFont(), 7, Font.BOLD, darkText ? INK : Color.WHITE);
            PdfPCell c = new PdfPCell(new Phrase(clean(value), f));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setBackgroundColor(color);
            c.setBorderColor(Color.WHITE);
            c.setBorderWidth(1);
            c.setFixedHeight(15);
            c.setPadding(1);
            table.addCell(c);
            return this;
        }

        /** Cellule simple (petite police), pour composer une ligne cellule par cellule. */
        public Table cell(String value, Align align, boolean bold) {
            PdfPCell c = plain(value, bold ? new Font(bodyBold.getBaseFont(), 7.5f, Font.NORMAL, INK) : small, align);
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(LINE);
            table.addCell(c);
            return this;
        }

        public boolean isEmpty() {
            return rows == 0;
        }

        public void end() {
            table.setSpacingAfter(6);
            add(table);
        }
    }

    private PdfPCell plain(String text, Font font, Align align) {
        PdfPCell c = new PdfPCell(new Phrase(clean(text), font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(3);
        c.setPaddingBottom(3);
        c.setHorizontalAlignment(switch (align) {
            case LEFT -> Element.ALIGN_LEFT;
            case RIGHT -> Element.ALIGN_RIGHT;
            case CENTER -> Element.ALIGN_CENTER;
        });
        return c;
    }

    public void pageBreak() {
        document.newPage();
    }

    private void add(Element e) {
        try {
            document.add(e);
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            document.close();
        } finally {
            out.close();
        }
    }

    /** Pied de page : entreprise · titre · page n / total (total écrit à la fermeture). */
    private static final class Footer extends PdfPageEventHelper {
        private final BaseFont font;
        private final Header header;
        private PdfTemplate total;

        Footer(BaseFont font, Header header) {
            this.font = font;
            this.header = header;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 22;
            Font f = new Font(font, 7.5f, Font.NORMAL, MUTED);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(clean(header.company() + " · " + header.title() + " · " + header.reference()), f),
                    document.left(), y, 0);
            String page = "Page " + writer.getPageNumber() + " / ";
            float width = font.getWidthPoint(page, 7.5f);
            float x = document.right() - width - 14;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(page, f), x, y, 0);
            cb.addTemplate(total, x + width, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(font, 7.5f);
            total.setColorFill(MUTED);
            total.setTextMatrix(0, 0);
            total.showText(Integer.toString(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
