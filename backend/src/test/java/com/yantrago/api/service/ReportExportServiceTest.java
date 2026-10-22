package com.yantrago.api.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ReportExportService — SEC-18 CSV formula injection neutralization.
 */
class ReportExportServiceTest {

    private final ReportExportService service = new ReportExportService();

    @Test
    void exportCsv_formulaStartingEquals_neutralized() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "=cmd|'/c calc'!A0", "value", "42")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("'=cmd"));
    }

    @Test
    void exportCsv_formulaStartingPlus_neutralized() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "+cmd|'/c calc'!A0", "value", "42")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("'+cmd"));
    }

    @Test
    void exportCsv_formulaStartingMinus_neutralized() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "-cmd|'/c calc'!A0", "value", "42")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("'-cmd"));
    }

    @Test
    void exportCsv_formulaStartingAt_neutralized() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "@SUM(A1:A10)", "value", "42")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("'@SUM"));
    }

    @Test
    void exportCsv_formulaAfterTab_neutralized() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "\t=cmd", "value", "42")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("'\t=cmd"));
    }

    @Test
    void exportCsv_negativeNumber_preserved() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine-01", "value", "-123.45")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("-123.45"));
    }

    @Test
    void exportCsv_positiveNumber_preserved() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine-01", "value", "+45.67")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("+45.67"));
    }

    @Test
    void exportCsv_exponentNumber_preserved() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine-01", "value", "-1.5e3")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("-1.5e3"));
    }

    @Test
    void exportCsv_stringNotFormula_preserved() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine-01", "value", "ok")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("ok"));
    }

    @Test
    void exportCsv_stringWithCommaAndQuote_escaped() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine, \"Test\"", "value", "ok")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("\"Machine, \"\"Test\"\"\""));
    }

    @Test
    void exportCsv_stringWithNewline_escaped() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine\nLine2", "value", "ok")
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("\"Machine\nLine2\""));
    }

    @Test
    void exportCsv_nullValue_emptyString() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Machine-01");
        row.put("value", null);

        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(row)
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("Machine-01,"));
    }

    @Test
    void exportCsv_emptyData_noDataFound() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of()
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("No data found"));
    }

    @Test
    void exportCsv_integerType_noQuote() {
        Map<String, Object> reportData = Map.of(
                "reportType", "test",
                "data", List.of(
                        Map.of("name", "Machine-01", "value", 42)
                )
        );

        byte[] csv = service.exportToCsv(reportData);
        String content = new String(csv);

        assertTrue(content.contains("42"));
    }
}
