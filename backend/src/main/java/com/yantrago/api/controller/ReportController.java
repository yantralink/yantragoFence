package com.yantrago.api.controller;

import com.yantrago.api.dto.report.ReportFilterDto;
import com.yantrago.api.service.ReportExportService;
import com.yantrago.api.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Report endpoints — generate and export reports.
 *
 * GET  /api/v1/reports — generate a report (JSON response)
 * POST /api/v1/reports/export — generate and export a report (PDF or CSV)
 *
 * Query params for GET:
 *   reportType=telemetry|location|alerts|commands|recharges|audit
 *   from=2026-09-01T00:00:00
 *   to=2026-09-05T00:00:00
 *   machineId={uuid} (optional)
 *
 * Body for POST:
 *   ReportFilterDto + format=pdf|csv
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestParam String reportType,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) java.util.UUID machineId) {

        java.time.LocalDateTime fromDt = java.time.LocalDateTime.parse(from);
        java.time.LocalDateTime toDt = java.time.LocalDateTime.parse(to);

        return ResponseEntity.ok(reportService.generateReport(reportType, fromDt, toDt, machineId));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportReport(@Valid @RequestBody ReportExportRequest request) throws Exception {
        ReportFilterDto filter = request.getFilter();
        Map<String, Object> reportData = reportService.generateReport(
                filter.getReportType(), filter.getFrom(), filter.getTo(), filter.getMachineId());

        String format = request.getFormat();
        byte[] content;
        MediaType mediaType;
        String filename;

        if ("pdf".equalsIgnoreCase(format)) {
            content = reportExportService.exportToPdf(reportData);
            mediaType = MediaType.APPLICATION_PDF;
            filename = "report_" + filter.getReportType() + "_" + System.currentTimeMillis() + ".pdf";
        } else if ("csv".equalsIgnoreCase(format)) {
            content = reportExportService.exportToCsv(reportData);
            mediaType = MediaType.parseMediaType("text/csv");
            filename = "report_" + filter.getReportType() + "_" + System.currentTimeMillis() + ".csv";
        } else {
            throw new IllegalArgumentException("Unsupported export format: " + format + " (use pdf or csv)");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(content);
    }

    /**
     * Request body for report export.
     */
    public static class ReportExportRequest {
        @Valid
        private ReportFilterDto filter;
        private String format; // pdf | csv

        public ReportFilterDto getFilter() { return filter; }
        public void setFilter(ReportFilterDto filter) { this.filter = filter; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }
}
