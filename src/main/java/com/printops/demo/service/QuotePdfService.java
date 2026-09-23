// src/main/java/com/printops/demo/service/QuotePdfService.java
package com.printops.demo.service;

import java.awt.Color;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.printops.demo.entity.Quote;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Genera el PDF profesional del presupuesto (US-11) con OpenPDF.
// Solo muestra precios visibles al cliente: nunca costos internos ni margen.
@Service
public class QuotePdfService {

    private static final Color OLIVE = new Color(0x60, 0x6c, 0x38);
    private static final Color BLACK_FOREST = new Color(0x28, 0x36, 0x18);
    private static final Color GRAY = new Color(0x66, 0x66, 0x66);
    private static final Color WHITE = Color.WHITE;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generatePdf(Quote q) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 48, 64);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new QuoteFooter());
        doc.open();

        // ── Encabezado ──
        Paragraph app = new Paragraph("PrintOps", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BLACK_FOREST));
        app.setSpacingAfter(2);
        doc.add(app);

        Paragraph num = new Paragraph("Presupuesto " + safe(q.getQuoteNumber()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, OLIVE));
        num.setSpacingAfter(2);
        doc.add(num);

        String date = q.getCreatedAt() != null ? q.getCreatedAt().format(DATE_FMT) : "";
        doc.add(new Paragraph("Fecha: " + date, FontFactory.getFont(FontFactory.HELVETICA, 11, GRAY)));

        doc.add(new Paragraph(" "));
        doc.add(horizontalLine());

        // ── Datos del cliente ──
        doc.add(sectionTitle("Datos del cliente"));
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 11, BLACK_FOREST);
        doc.add(new Paragraph("Cliente: " + valueOrDash(q.getClientName()), body));
        doc.add(new Paragraph("Trabajo: " + valueOrDash(q.getJobDescription()), body));
        doc.add(new Paragraph("Material: " + (q.getFilamentType() != null ? q.getFilamentType().name() : "-"), body));

        double totalGrams = nz(q.getFilamentGrams()) * units(q.getUnits());
        doc.add(new Paragraph("Filamento total: " + fmt(totalGrams) + " g", body));
        double totalHours = nz(q.getPrintingHours()) * units(q.getUnits());
        doc.add(new Paragraph("Tiempo estimado de entrega: " + fmt(totalHours) + " horas", body));

        doc.add(new Paragraph(" "));

        // ── Tabla de precios (visible al cliente) ──
        doc.add(sectionTitle("Detalle de precios"));
        doc.add(priceTable(q));

        doc.close();
        return out.toByteArray();
    }

    private PdfPTable priceTable(Quote q) {
        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        try {
            t.setWidths(new float[]{1.2f, 1f, 1f, 1f, 1.3f});
        } catch (DocumentException ignored) {
        }

        addHeaderCell(t, "Precio unitario");
        addHeaderCell(t, "Cantidad");
        addHeaderCell(t, "Subtotal");
        addHeaderCell(t, "Descuento");
        addHeaderCell(t, "Total");

        double unitPrice = nz(q.getUnitPrice());
        int units = units(q.getUnits());
        double subtotal = unitPrice * units;
        double totalPrice = nz(q.getTotalPrice());
        double discount = Math.max(0.0, subtotal - totalPrice);

        addCell(t, money(unitPrice));
        addCell(t, String.valueOf(units));
        addCell(t, money(subtotal));
        addCell(t, money(discount));
        addTotalCell(t, money(totalPrice));

        return t;
    }

    private void addHeaderCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, WHITE)));
        c.setBackgroundColor(OLIVE);
        c.setBorderColor(BLACK_FOREST);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private void addCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 10, BLACK_FOREST)));
        c.setBorderColor(GRAY);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private void addTotalCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BLACK_FOREST)));
        c.setBorderColor(BLACK_FOREST);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, OLIVE));
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPTable horizontalLine() {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(""));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(BLACK_FOREST);
        c.setBorderWidthBottom(1f);
        t.addCell(c);
        return t;
    }

    // Pie de página: validez, marca y número de página.
    private static class QuoteFooter extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font f = FontFactory.getFont(FontFactory.HELVETICA, 8, GRAY);
            PdfPTable t = new PdfPTable(3);
            t.setTotalWidth(document.getPageSize().getWidth() - 72);
            try {
                t.setWidths(new float[]{1.4f, 1.8f, 0.8f});
            } catch (DocumentException ignored) {
            }
            t.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            t.addCell(new Paragraph("Validez del presupuesto: 15 días", f));
            t.addCell(new Paragraph("PrintOps — Gestión de Impresión 3D", f));

            Paragraph page = new Paragraph("Página " + writer.getPageNumber(), f);
            page.setAlignment(Element.ALIGN_RIGHT);
            t.addCell(page);

            t.writeSelectedRows(0, -1, 36, 40, writer.getDirectContent());
        }
    }

    private String money(double v) {
        return String.format(Locale.US, "$%.2f", v);
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private int units(Integer u) {
        return u != null ? u : 1;
    }

    private double nz(Double d) {
        return d != null ? d : 0.0;
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }

    private String valueOrDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
