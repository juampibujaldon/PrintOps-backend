// src/main/java/com/printops/demo/service/QuotePdfService.java
package com.printops.demo.service;

import java.awt.Color;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.printops.demo.entity.Quote;
import com.printops.demo.entity.QuoteStatus;
import com.printops.demo.entity.Workspace;
import com.printops.demo.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Genera el PDF del presupuesto (US-11) con OpenPDF, en blanco y celeste.
// Solo muestra precios visibles al cliente: nunca costos internos ni margen.
@Service
public class QuotePdfService {

    // ── Paleta blanco + celeste ──
    private static final Color CELESTE = new Color(0x29, 0xA8, 0xE0);        // acento principal
    private static final Color CELESTE_DARK = new Color(0x0E, 0x6F, 0x9E);   // títulos sobre fondo claro
    private static final Color CELESTE_LIGHT = new Color(0xEA, 0xF6, 0xFC);  // fondos de tarjetas
    private static final Color CELESTE_BORDER = new Color(0xC7, 0xE6, 0xF5); // bordes suaves
    private static final Color INK = new Color(0x1F, 0x2D, 0x3A);            // texto principal
    private static final Color MUTED = new Color(0x6B, 0x7C, 0x8A);          // texto secundario
    private static final Color WHITE = Color.WHITE;
    private static final Color STATUS_GRAY = new Color(0x8A, 0x97, 0xA3);
    private static final Color STATUS_GREEN = new Color(0x2E, 0x9E, 0x5B);
    private static final Color STATUS_RED = new Color(0xD9, 0x48, 0x3B);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final int DEFAULT_VALIDITY_DAYS = 15;

    private final WorkspaceRepository workspaceRepository;
    private final com.printops.demo.storage.FileStorage fileStorage;

    public QuotePdfService(WorkspaceRepository workspaceRepository, com.printops.demo.storage.FileStorage fileStorage) {
        this.workspaceRepository = workspaceRepository;
        this.fileStorage = fileStorage;
    }

    public byte[] generatePdf(Quote q) {
        Workspace ws = q.getWorkspaceId() != null
                ? workspaceRepository.findById(q.getWorkspaceId()).orElse(null)
                : null;
        int validityDays = ws != null && ws.getQuoteValidityDays() != null && ws.getQuoteValidityDays() > 0
                ? ws.getQuoteValidityDays() : DEFAULT_VALIDITY_DAYS;
        String currency = ws != null && ws.getCurrency() != null ? ws.getCurrency() : "ARS";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 44, 70);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        // La marca de agua es opcional por presupuesto y nunca aparece fuera de borrador.
        writer.setPageEvent(new QuoteFrame(validityDays, ws != null ? ws.getName() : null,
                q.getStatus() == QuoteStatus.DRAFT && q.isDraftWatermark()));
        doc.open();

        doc.add(header(q, ws, validityDays));
        doc.add(spacer(18));
        doc.add(clientCard(q));
        doc.add(spacer(18));
        doc.add(sectionTitle("Detalle"));
        doc.add(itemsTable(q));
        doc.add(spacer(10));
        doc.add(totals(q));
        doc.add(spacer(22));
        doc.add(conditions(validityDays, currency));

        doc.close();
        return out.toByteArray();
    }

    // ── Encabezado: marca/emisor a la izquierda, datos del presupuesto a la derecha ──
    private PdfPTable header(Quote q, Workspace ws, int validityDays) {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        setWidths(t, 1.35f, 1f);

        // Izquierda: logo (si el taller subió uno), nombre del taller (o PrintOps) + contacto.
        PdfPCell left = noBorder(new PdfPCell());
        left.setVerticalAlignment(Element.ALIGN_TOP);
        Image logo = logo(ws);
        if (logo != null) {
            left.addElement(logo);
        }
        String brand = ws != null && notBlank(ws.getName()) ? ws.getName() : "PrintOps";
        left.addElement(new Paragraph(brand, font(FontFactory.HELVETICA_BOLD, 22, INK)));
        Paragraph tagline = new Paragraph("Impresión 3D a medida", font(FontFactory.HELVETICA, 10, CELESTE_DARK));
        tagline.setSpacingAfter(6);
        left.addElement(tagline);
        if (ws != null) {
            for (String line : contactLines(ws)) {
                left.addElement(new Paragraph(line, font(FontFactory.HELVETICA, 9, MUTED)));
            }
        }
        t.addCell(left);

        // Derecha: caja celeste clara con número, fecha y vencimiento.
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell boxCell = new PdfPCell();
        boxCell.setBackgroundColor(CELESTE_LIGHT);
        boxCell.setBorderColor(CELESTE_BORDER);
        boxCell.setBorderWidth(0.8f);
        boxCell.setPadding(12);

        boxCell.addElement(new Paragraph("PRESUPUESTO", font(FontFactory.HELVETICA_BOLD, 9, CELESTE_DARK)));
        Paragraph number = new Paragraph(safe(q.getQuoteNumber()), font(FontFactory.HELVETICA_BOLD, 18, INK));
        number.setSpacingAfter(4);
        boxCell.addElement(number);
        boxCell.addElement(statusBadge(q.getStatus()));

        LocalDateTime created = q.getCreatedAt() != null ? q.getCreatedAt() : LocalDateTime.now();
        boxCell.addElement(labelValue("Emitido", created.format(DATE_FMT)));
        boxCell.addElement(labelValue("Válido hasta", created.plusDays(validityDays).format(DATE_FMT)));
        box.addCell(boxCell);

        PdfPCell right = noBorder(new PdfPCell(box));
        right.setVerticalAlignment(Element.ALIGN_TOP);
        t.addCell(right);
        return t;
    }

    // Logo escalado a una caja de 150 x 56 pt. Si la imagen está dañada se omite: el PDF sale igual.
    private Image logo(Workspace ws) {
        if (ws == null || ws.getLogoUrl() == null) return null;
        return fileStorage.read(ws.getLogoUrl()).map(bytes -> {
            try {
                Image img = Image.getInstance(bytes);
                img.scaleToFit(150, 56);
                img.setAlignment(Image.ALIGN_LEFT);
                img.setSpacingAfter(6);
                return img;
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }

    // ── Tarjeta del cliente y del trabajo ──
    private PdfPTable clientCard(Quote q) {
        int units = units(q.getUnits());
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        setWidths(t, 1f, 1f);

        PdfPCell client = cardCell();
        client.addElement(new Paragraph("CLIENTE", font(FontFactory.HELVETICA_BOLD, 8, CELESTE_DARK)));
        client.addElement(new Paragraph(valueOrDash(q.getClientName()), font(FontFactory.HELVETICA_BOLD, 13, INK)));
        Paragraph job = new Paragraph(valueOrDash(q.getJobDescription()), font(FontFactory.HELVETICA, 10, MUTED));
        job.setSpacingBefore(2);
        client.addElement(job);
        t.addCell(client);

        PdfPCell specs = cardCell();
        specs.addElement(new Paragraph("ESPECIFICACIONES", font(FontFactory.HELVETICA_BOLD, 8, CELESTE_DARK)));
        specs.addElement(labelValue("Material", q.getFilamentType() != null ? q.getFilamentType().name() : "-"));
        specs.addElement(labelValue("Cantidad", units + (units == 1 ? " unidad" : " unidades")));
        specs.addElement(labelValue("Filamento", number(nz(q.getFilamentGrams()) * units, 0) + " g"));
        specs.addElement(labelValue("Tiempo de impresión", number(nz(q.getPrintingHours()) * units, 1) + " h"));
        t.addCell(specs);
        return t;
    }

    // ── Tabla de ítems ──
    private PdfPTable itemsTable(Quote q) {
        int units = units(q.getUnits());
        double unitPrice = nz(q.getUnitPrice());

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        setWidths(t, 2.6f, 0.8f, 1.2f, 1.2f);
        t.setHeaderRows(1);

        headerCell(t, "Concepto", Element.ALIGN_LEFT);
        headerCell(t, "Cant.", Element.ALIGN_CENTER);
        headerCell(t, "Precio unit.", Element.ALIGN_RIGHT);
        headerCell(t, "Importe", Element.ALIGN_RIGHT);

        String concept = "Impresión 3D" + (notBlank(q.getJobDescription()) ? " — " + q.getJobDescription() : "");
        String material = q.getFilamentType() != null ? "Material " + q.getFilamentType().name() : null;

        PdfPCell conceptCell = bodyCell(null, Element.ALIGN_LEFT);
        conceptCell.addElement(new Paragraph(concept, font(FontFactory.HELVETICA_BOLD, 10, INK)));
        if (material != null) {
            conceptCell.addElement(new Paragraph(material, font(FontFactory.HELVETICA, 9, MUTED)));
        }
        t.addCell(conceptCell);
        t.addCell(bodyCell(String.valueOf(units), Element.ALIGN_CENTER));
        t.addCell(bodyCell(money(unitPrice), Element.ALIGN_RIGHT));
        t.addCell(bodyCell(money(unitPrice * units), Element.ALIGN_RIGHT));

        // Gastos extra: cada uno como línea propia (se cobran a costo).
        for (var extra : q.getExtraCosts()) {
            PdfPCell c = bodyCell(null, Element.ALIGN_LEFT);
            c.addElement(new Paragraph(extra.getDescription(), font(FontFactory.HELVETICA_BOLD, 10, INK)));
            c.addElement(new Paragraph("Gasto adicional", font(FontFactory.HELVETICA, 9, MUTED)));
            t.addCell(c);
            t.addCell(bodyCell("1", Element.ALIGN_CENTER));
            t.addCell(bodyCell(money(nz(extra.getAmount())), Element.ALIGN_RIGHT));
            t.addCell(bodyCell(money(nz(extra.getAmount())), Element.ALIGN_RIGHT));
        }
        return t;
    }

    // ── Resumen: subtotal, descuento y total destacado ──
    private PdfPTable totals(Quote q) {
        int units = units(q.getUnits());
        double extras = nz(q.getExtraCostsTotal());
        double subtotal = nz(q.getUnitPrice()) * units + extras;
        double total = nz(q.getTotalPrice());
        // El descuento aplica solo a la impresión; los extras van a costo.
        double discount = Math.max(0.0, subtotal - total);

        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        setWidths(wrapper, 1.25f, 1f);
        wrapper.addCell(noBorder(new PdfPCell(new Phrase(""))));

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        setWidths(t, 1f, 1f);
        summaryRow(t, "Subtotal", money(subtotal));
        if (discount > 0.004) {
            String pct = q.getDiscountPercent() != null && q.getDiscountPercent() > 0
                    ? " (" + number(q.getDiscountPercent(), 0) + "%)" : "";
            summaryRow(t, "Descuento" + pct, "− " + money(discount));
        }

        // Una sola celda celeste (colspan 2) con la fila adentro: con dos celdas
        // separadas el visor dibujaba una línea blanca entre ambas.
        PdfPTable totalRow = new PdfPTable(2);
        totalRow.setWidthPercentage(100);
        setWidths(totalRow, 1f, 1.4f);
        PdfPCell totalLabel = noBorder(new PdfPCell(new Phrase("TOTAL", font(FontFactory.HELVETICA_BOLD, 11, WHITE))));
        PdfPCell totalValue = noBorder(new PdfPCell(new Phrase(money(total), font(FontFactory.HELVETICA_BOLD, 15, WHITE))));
        totalLabel.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalValue.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalRow.addCell(totalLabel);
        totalRow.addCell(totalValue);

        PdfPCell totalBox = new PdfPCell(totalRow);
        totalBox.setColspan(2);
        totalBox.setBackgroundColor(CELESTE);
        totalBox.setBorder(Rectangle.NO_BORDER);
        totalBox.setPaddingTop(10);
        totalBox.setPaddingBottom(12);
        totalBox.setPaddingLeft(10);
        totalBox.setPaddingRight(10);
        t.addCell(totalBox);

        wrapper.addCell(noBorder(new PdfPCell(t)));
        return wrapper;
    }

    // Estado del presupuesto como "pastilla" de color con texto blanco.
    private Paragraph statusBadge(QuoteStatus status) {
        String label;
        Color color;
        switch (status != null ? status : QuoteStatus.DRAFT) {
            case SENT -> { label = "ENVIADO"; color = CELESTE; }
            case ACCEPTED -> { label = "ACEPTADO"; color = STATUS_GREEN; }
            case PAID -> { label = "PAGADO"; color = STATUS_GREEN; }
            case REJECTED -> { label = "RECHAZADO"; color = STATUS_RED; }
            default -> { label = "BORRADOR"; color = STATUS_GRAY; }
        }
        Chunk chunk = new Chunk(" " + label + " ", font(FontFactory.HELVETICA_BOLD, 8, WHITE));
        chunk.setBackground(color, 3f, 3f, 3f, 3f);
        Paragraph p = new Paragraph(chunk);
        p.setSpacingBefore(2);
        p.setSpacingAfter(8);
        return p;
    }

    private Paragraph conditions(int validityDays, String currency) {
        Paragraph p = new Paragraph();
        p.add(new Chunk("Condiciones\n", font(FontFactory.HELVETICA_BOLD, 9, CELESTE_DARK)));
        p.add(new Chunk(
                "• Presupuesto válido por " + validityDays + " días desde su emisión.\n"
                        + "• Importes expresados en " + currency + ".\n"
                        + "• Los tiempos de impresión son estimados y pueden variar según la complejidad de la pieza.",
                font(FontFactory.HELVETICA, 9, MUTED)));
        p.setLeading(13);
        return p;
    }

    // ── Celdas y helpers de estilo ──
    private void headerCell(PdfPTable t, String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(FontFactory.HELVETICA_BOLD, 9, CELESTE_DARK)));
        c.setBackgroundColor(CELESTE_LIGHT);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(CELESTE);
        c.setBorderWidthBottom(1.2f);
        c.setPadding(8);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private PdfPCell bodyCell(String text, int align) {
        PdfPCell c = text != null
                ? new PdfPCell(new Phrase(text, font(FontFactory.HELVETICA, 10, INK)))
                : new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(CELESTE_BORDER);
        c.setBorderWidthBottom(0.8f);
        c.setPaddingTop(9);
        c.setPaddingBottom(11);
        c.setPaddingLeft(8);
        c.setPaddingRight(8);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private void summaryRow(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, font(FontFactory.HELVETICA, 10, MUTED)));
        PdfPCell v = new PdfPCell(new Phrase(value, font(FontFactory.HELVETICA, 10, INK)));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingTop(4);
            c.setPaddingBottom(6);
            c.setPaddingLeft(10);
            c.setPaddingRight(10);
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private PdfPCell cardCell() {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(CELESTE_LIGHT);
        c.setBorderColor(WHITE);
        c.setBorderWidth(4f); // borde blanco = separación entre las dos tarjetas
        c.setPaddingTop(10);
        c.setPaddingBottom(14);
        c.setPaddingLeft(12);
        c.setPaddingRight(12);
        return c;
    }

    private Paragraph labelValue(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", font(FontFactory.HELVETICA, 9, MUTED)));
        p.add(new Chunk(value, font(FontFactory.HELVETICA_BOLD, 9, INK)));
        p.setLeading(14);
        return p;
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text.toUpperCase(AR), font(FontFactory.HELVETICA_BOLD, 9, CELESTE_DARK));
        p.setSpacingAfter(6);
        return p;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ", font(FontFactory.HELVETICA, 1, WHITE));
        p.setSpacingAfter(height);
        return p;
    }

    private PdfPCell noBorder(PdfPCell c) {
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private void setWidths(PdfPTable t, float... widths) {
        try {
            t.setWidths(widths);
        } catch (DocumentException ignored) {
        }
    }

    private List<String> contactLines(Workspace ws) {
        List<String> lines = new ArrayList<>();
        if (notBlank(ws.getAddress())) lines.add(ws.getAddress());
        if (notBlank(ws.getPhone())) lines.add("Tel. " + ws.getPhone());
        if (notBlank(ws.getContactEmail())) lines.add(ws.getContactEmail());
        return lines;
    }

    private static Font font(String name, float size, Color color) {
        return FontFactory.getFont(name, size, color);
    }

    // Marco de cada página: franja celeste arriba y pie con línea, validez y número.
    private static class QuoteFrame extends PdfPageEventHelper {
        private final int validityDays;
        private final String issuer;
        private final boolean draft;

        QuoteFrame(int validityDays, String issuer, boolean draft) {
            this.validityDays = validityDays;
            this.issuer = issuer;
            this.draft = draft;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContentUnder();
            Rectangle page = document.getPageSize();

            // Franja superior celeste.
            cb.saveState();
            cb.setColorFill(CELESTE);
            cb.rectangle(0, page.getHeight() - 8, page.getWidth(), 8);
            cb.fill();

            // Línea del pie.
            cb.setColorStroke(CELESTE_BORDER);
            cb.setLineWidth(0.8f);
            cb.moveTo(40, 52);
            cb.lineTo(page.getWidth() - 40, 52);
            cb.stroke();
            cb.restoreState();

            // Marca de agua en borradores: evita que se tomen como versión final.
            if (draft) {
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase("BORRADOR", font(FontFactory.HELVETICA_BOLD, 72, CELESTE_LIGHT)),
                        page.getWidth() / 2, page.getHeight() * 0.40f, 40);
            }

            Font f = font(FontFactory.HELVETICA, 8, MUTED);
            PdfPTable t = new PdfPTable(3);
            t.setTotalWidth(page.getWidth() - 80);
            try {
                t.setWidths(new float[]{1.3f, 1.8f, 0.7f});
            } catch (DocumentException ignored) {
            }
            t.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            t.addCell(new Phrase("Validez: " + validityDays + " días", f));
            PdfPCell center = new PdfPCell(new Phrase(
                    (issuer != null && !issuer.isBlank() ? issuer + " · " : "") + "Generado con PrintOps", f));
            center.setBorder(Rectangle.NO_BORDER);
            center.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(center);
            PdfPCell pageCell = new PdfPCell(new Phrase("Página " + writer.getPageNumber(), f));
            pageCell.setBorder(Rectangle.NO_BORDER);
            pageCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(pageCell);
            t.writeSelectedRows(0, -1, 40, 46, writer.getDirectContent());
        }
    }

    // ── Formato ──
    private String money(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(AR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "$ " + nf.format(v);
    }

    private String number(double v, int decimals) {
        NumberFormat nf = NumberFormat.getNumberInstance(AR);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(decimals);
        return nf.format(v);
    }

    private int units(Integer u) {
        return u != null ? u : 1;
    }

    private double nz(Double d) {
        return d != null ? d : 0.0;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }

    private String valueOrDash(String s) {
        return notBlank(s) ? s : "-";
    }
}
