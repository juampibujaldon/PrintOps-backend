package com.printops.demo.util;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// CSV pensado para Excel en español: BOM UTF-8 (acentos bien), separador ";" y coma decimal.
// Las celdas que empiezan con = + - @ se prefijan con ' para que Excel no las ejecute como
// fórmulas (inyección CSV con datos que cargó un usuario).
public final class Csv {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final StringBuilder out = new StringBuilder("﻿");

    public Csv row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.append(';');
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    public Csv row(Object... cells) {
        return row(java.util.Arrays.asList(cells));
    }

    public byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(Object v) {
        if (v == null) return "";
        String s;
        if (v instanceof Double || v instanceof Float) {
            DecimalFormat f = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
            s = f.format(((Number) v).doubleValue());
        } else if (v instanceof Instant i) {
            s = DATE_TIME.format(i.atZone(ZONE));
        } else if (v instanceof LocalDateTime t) {
            s = DATE_TIME.format(t);
        } else if (v instanceof LocalDate d) {
            s = DATE.format(d);
        } else {
            s = v.toString();
            if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        }
        if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
