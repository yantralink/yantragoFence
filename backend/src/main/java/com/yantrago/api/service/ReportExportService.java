package com.yantrago.api.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Report export service — exports report data to PDF (via PDFBox) and CSV.
 *
 * Per AGENTS.md rule 12: production feature requiring logging + error handling.
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);

    /**
     * Exports report data to a PDF document.
     *
     * @param reportData the report data map (must contain "data" as List<Map<String, Object>>)
     * @return PDF bytes
     */
    public byte[] exportToPdf(Map<String, Object> reportData) throws IOException {
        log.info("Exporting report to PDF: type={}", reportData.get("reportType"));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                contentStream.setLeading(20f);
                contentStream.newLineAtOffset(50, 750);

                // Title
                String title = "YantraGO Report: " + reportData.getOrDefault("reportType", "Unknown");
                contentStream.showText(title);
                contentStream.newLine();

                // Metadata
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                contentStream.showText("From: " + reportData.getOrDefault("from", ""));
                contentStream.newLine();
                contentStream.showText("To: " + reportData.getOrDefault("to", ""));
                contentStream.newLine();
                contentStream.showText("Generated: " + reportData.getOrDefault("generatedAt", ""));
                contentStream.newLine();
                contentStream.showText("Rows: " + reportData.getOrDefault("rowCount", 0));
                contentStream.newLine();
                contentStream.newLine();

                // Data rows
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) reportData.get("data");
                if (data != null && !data.isEmpty()) {
                    // Header
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
                    String header = String.join(" | ", data.get(0).keySet());
                    contentStream.showText(header.length() > 100 ? header.substring(0, 100) + "..." : header);
                    contentStream.newLine();

                    // Rows
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                    for (Map<String, Object> row : data) {
                        String rowStr = String.join(" | ", row.values().stream()
                                .map(v -> v != null ? v.toString() : "")
                                .toList());
                        if (rowStr.length() > 100) {
                            rowStr = rowStr.substring(0, 100) + "...";
                        }
                        contentStream.showText(rowStr);
                        contentStream.newLine();
                    }
                } else {
                    contentStream.showText("No data found for the selected criteria.");
                }

                contentStream.endText();
            }

            document.save(outputStream);
            log.info("PDF export complete: {} bytes", outputStream.size());
            return outputStream.toByteArray();
        }
    }

    /**
     * Returns true if the string starts with a formula trigger character
     * that spreadsheet applications (Excel, LibreOffice) would interpret
     * as a formula.
     *
     * Covers: = + - @ and control characters (tab \t, CR \r, LF \n)
     * that can precede the formula trigger.
     *
     * Numeric-looking strings (e.g. "-123", "+45.67", "-1.5e3") are
     * excluded — they are treated as numbers by the CSV consumer, not formulas.
     */
    private boolean isFormulaLike(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }

        // Check for control characters at the start
        char first = s.charAt(0);
        if (first == '\t' || first == '\r' || first == '\n' || first == 0x0B || first == 0x0C) {
            return true;
        }

        // Direct formula triggers: = @
        if (first == '=' || first == '@') {
            return true;
        }

        // + and - are ambiguous: could be a number or a formula.
        // If the remainder looks like a number, it's numeric — not a formula.
        if (first == '+' || first == '-') {
            String rest = s.substring(1);
            return !isNumeric(rest);
        }

        return false;
    }

    /**
     * Returns true if the string consists only of characters that could
     * form a valid number (digits, decimal point, exponent, sign).
     */
    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int len = s.length();
        boolean hasDigit = false;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (c == '.' || c == '+' || c == '-' || c == 'e' || c == 'E') {
                // allowed in numeric strings
            } else {
                return false;
            }
        }
        return hasDigit;
    }

    /**
     * Exports report data to CSV format.
     *
     * @param reportData the report data map (must contain "data" as List<Map<String, Object>>)
     * @return CSV bytes
     */
    public byte[] exportToCsv(Map<String, Object> reportData) {
        log.info("Exporting report to CSV: type={}", reportData.get("reportType"));

        StringBuilder csv = new StringBuilder();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) reportData.get("data");

        if (data != null && !data.isEmpty()) {
            // Header
            csv.append(String.join(",", data.get(0).keySet())).append("\n");

            // Rows
            for (Map<String, Object> row : data) {
                csv.append(String.join(",", row.values().stream()
                        .map(v -> {
                            if (v == null) return "";
                            // SEC-18: neutralize spreadsheet formula injection.
                            // Only String values are checked — actual Number types
                            // are real numeric values and don't need protection.
                            String s = v.toString();
                            if (v instanceof String str && isFormulaLike(str)) {
                                s = "'" + str;
                            }
                            // Escape CSV values containing commas or quotes
                            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                                return "\"" + s.replace("\"", "\"\"") + "\"";
                            }
                            return s;
                        })
                        .toList())).append("\n");
            }
        } else {
            csv.append("No data found\n");
        }

        log.info("CSV export complete: {} bytes", csv.length());
        return csv.toString().getBytes();
    }
}
