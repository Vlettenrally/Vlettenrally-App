package nl.vlettenrally.util;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RFC-4180 CSV writing, shared by the exports.
 * <p>
 * The delimiter is per call rather than fixed: the tracker export is comma-separated
 * because it is read by the tracker provider's tooling, while the ranking export is
 * semicolon-separated because it is opened by hand in a Dutch-locale Excel.
 */
public final class Csv {

    /**
     * Byte-order mark. Excel only reads a UTF-8 CSV as UTF-8 when the file starts with
     * this; without it the Dutch team and group names come out mojibaked.
     */
    public static final String UTF8_BOM = "﻿";

    private Csv() {
    }

    /** Quote fields containing the delimiter, a quote, or a line break; double inner quotes. */
    public static String escape(String value, char delimiter) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(delimiter) >= 0 || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** Append one CSV line, escaping each cell. */
    public static void appendRow(StringBuilder sb, char delimiter, Stream<String> cells) {
        sb.append(cells.map(cell -> escape(cell, delimiter)).collect(Collectors.joining(String.valueOf(delimiter))))
                .append('\n');
    }
}
