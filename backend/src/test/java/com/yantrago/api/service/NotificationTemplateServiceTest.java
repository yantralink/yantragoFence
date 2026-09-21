package com.yantrago.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationTemplateService} (Multilingual Plan
 * Phase 6):
 * - renders in the requested locale
 * - falls back to English when the requested locale has no template
 * - missing template parameters never leak raw {placeholders} into
 *   user-visible text
 * - returns null (caller applies its own fallback) when no template exists
 */
class NotificationTemplateServiceTest {

    private JdbcTemplate jdbcTemplate;
    private NotificationTemplateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new NotificationTemplateService(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private void seedTemplate(String title, String body) {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class),
                any(), any(), any(), any()))
                .thenAnswer(inv -> new NotificationTemplateService.TemplateRow(title, body, 1));
    }

    @SuppressWarnings("unchecked")
    private void seedMissing() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class),
                any(), any(), any(), any()))
                .thenThrow(new EmptyResultDataAccessException(1));
    }

    @Test
    @DisplayName("renders placeholders in the requested locale")
    void rendersRequestedLocale() {
        seedTemplate("Low Battery Alert", "Battery is low on machine {machineName}: {observedValue}%");
        var result = service.render(UUID.randomUUID(), "LOW_BATTERY", "OPEN", "mr",
                Map.of("machineName", "M1", "observedValue", "12"));
        assertEquals("Low Battery Alert", result.title());
        assertEquals("Battery is low on machine M1: 12%", result.body());
        assertEquals(1, result.templateVersion());
    }

    @Test
    @DisplayName("unresolved placeholders are replaced with a safe substitute")
    void missingParametersNeverLeakPlaceholders() {
        seedTemplate("Alert", "Battery low on {machineName} at {observedValue} and {unknownVar}");
        var result = service.render(UUID.randomUUID(), "LOW_BATTERY", "OPEN", "en",
                Map.of("machineName", "M1" /* observedValue missing, unknownVar unknown */));
        assertEquals("Battery low on M1 at - and -", result.body());
    }

    @Test
    @DisplayName("empty variable map still sanitizes the template")
    void emptyVariablesSanitized() {
        seedTemplate("Alert", "Value is {observedValue}");
        var result = service.render(UUID.randomUUID(), "LOW_BATTERY", "OPEN", "en", Map.of());
        assertEquals("Value is -", result.body());
    }

    @Test
    @DisplayName("null variable values are replaced with the safe substitute")
    void nullVariableValuesSanitized() {
        seedTemplate("Alert", "Machine {machineName} value {observedValue}");
        var permissive = new java.util.HashMap<String, String>();
        permissive.put("machineName", "M1");
        permissive.put("observedValue", null);
        var result = service.render(UUID.randomUUID(), "LOW_BATTERY", "OPEN", "en", permissive);
        assertEquals("Machine M1 value -", result.body());
    }

    @Test
    @DisplayName("sanitizePlaceholders handles multiple and repeated tokens")
    void sanitizeUtility() {
        assertEquals("- and -", NotificationTemplateService.sanitizePlaceholders("{a} and {b_2}"));
        assertEquals("no tokens", NotificationTemplateService.sanitizePlaceholders("no tokens"));
        assertEquals("- {not a token}", NotificationTemplateService.sanitizePlaceholders("{x} {not a token}"));
    }

    @Test
    @DisplayName("falls back to English template when requested locale missing")
    void fallsBackToEnglish() {
        // First query (requested locale) → no row; second query (en) → row.
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class),
                any(), any(), any(), any()))
                .thenThrow(new EmptyResultDataAccessException(1))
                .thenAnswer(inv -> new NotificationTemplateService.TemplateRow(
                        "Low Battery Alert", "Battery low: {machineName}", 1));

        var result = service.render(UUID.randomUUID(), "LOW_BATTERY", "OPEN", "hi",
                Map.of("machineName", "M1"));
        assertEquals("Battery low: M1", result.body());
    }

    @Test
    @DisplayName("returns null when no template exists for any locale")
    void returnsNullWhenNoTemplate() {
        seedMissing();
        assertNull(service.render(UUID.randomUUID(), "UNKNOWN_TYPE", "OPEN", "en", Map.of()));
    }
}
