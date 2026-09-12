package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves and renders notification templates for alert types and locales.
 *
 * Per notification plan Phase 3:
 * - Templates for all enabled event types
 * - Safe display content
 * - Locale/template version
 * - Fallback text
 *
 * Templates are stored in notification_templates table and support placeholders
 * like {machineName}, {observedValue}, {observedUnit}.
 */
@Service
public class NotificationTemplateService {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateService.class);
    private static final String DEFAULT_LOCALE = "en";

    private final JdbcTemplate jdbcTemplate;

    public NotificationTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Renders a notification template for the given alert type, state, and locale.
     *
     * @param organizationId the organization (for org-specific templates, null for system)
     * @param alertType the alert type (LOW_BATTERY, DEVICE_OFFLINE, etc.)
     * @param incidentState the incident state (OPEN, RESOLVED, ESCALATED)
     * @param locale the preferred locale (e.g. "en", "hi", "mr")
     * @param variables placeholder values (machineName, observedValue, observedUnit)
     * @return rendered template, or null if no template found
     */
    public RenderedTemplate render(UUID organizationId, String alertType, String incidentState,
                                    String locale, Map<String, String> variables) {
        String effectiveLocale = locale != null ? locale : DEFAULT_LOCALE;

        // Try org-specific template first, then system default, then locale fallback
        TemplateRow template = findTemplate(organizationId, alertType, incidentState, effectiveLocale);
        if (template == null && !DEFAULT_LOCALE.equals(effectiveLocale)) {
            template = findTemplate(organizationId, alertType, incidentState, DEFAULT_LOCALE);
        }

        if (template == null) {
            log.warn("No template found for alertType={} state={} locale={}", alertType, incidentState, effectiveLocale);
            return null;
        }

        String title = renderTemplate(template.titleTemplate(), variables);
        String body = renderTemplate(template.bodyTemplate(), variables);

        return new RenderedTemplate(title, body, template.templateVersion());
    }

    private TemplateRow findTemplate(UUID organizationId, String alertType, String incidentState, String locale) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT title_template, body_template, template_version " +
                            "FROM notification_templates " +
                            "WHERE (organization_id = ? OR organization_id IS NULL) " +
                            "AND alert_type = ? AND incident_state = ? AND locale = ? " +
                            "AND is_active = true " +
                            "ORDER BY organization_id NULLS LAST, template_version DESC " +
                            "LIMIT 1",
                    (rs, rowNum) -> new TemplateRow(
                            rs.getString("title_template"),
                            rs.getString("body_template"),
                            rs.getInt("template_version")
                    ),
                    organizationId, alertType, incidentState, locale
            );
        } catch (Exception e) {
            log.debug("No template found for alertType={} state={} locale={}: {}",
                    alertType, incidentState, locale, e.getMessage());
            return null;
        }
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) return "";
        if (variables == null || variables.isEmpty()) return template;

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    // ===== Inner types =====

    public record RenderedTemplate(String title, String body, int templateVersion) {}

    private record TemplateRow(String titleTemplate, String bodyTemplate, int templateVersion) {}
}
