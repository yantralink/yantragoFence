package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Matches {name} placeholders left unsubstituted after rendering. */
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{[a-zA-Z_][a-zA-Z0-9_]*}");
    /** Safe substitute for missing parameters — never leak raw {placeholders} to users. */
    private static final String MISSING_PARAMETER_SUBSTITUTE = "-";

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

        String title = renderTemplate(alertType, template.titleTemplate(), variables);
        String body = renderTemplate(alertType, template.bodyTemplate(), variables);

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

    /**
     * Substitutes variables and sanitizes the result.
     *
     * Per Multilingual Plan Phase 6: missing template parameters must never
     * leak raw {placeholders} into user-visible text. Any token without a
     * provided value (or with an empty variable map) is replaced with a safe
     * "-" substitute and a sanitized diagnostic is logged (no user content).
     */
    private String renderTemplate(String alertType, String template, Map<String, String> variables) {
        if (template == null) return "";

        String result = template;
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue() : MISSING_PARAMETER_SUBSTITUTE;
                result = result.replace(placeholder, value);
            }
        }

        Matcher unresolved = UNRESOLVED_PLACEHOLDER.matcher(result);
        if (unresolved.find()) {
            int count = 1;
            while (unresolved.find()) count++;
            log.warn("Notification template for alertType={} had {} unresolved placeholder(s); "
                    + "replaced with safe substitute", alertType, count);
            return sanitizePlaceholders(result);
        }
        return result;
    }

    /** Replaces any remaining {placeholder} tokens with the safe substitute. */
    static String sanitizePlaceholders(String text) {
        return UNRESOLVED_PLACEHOLDER.matcher(text)
                .replaceAll(Matcher.quoteReplacement(MISSING_PARAMETER_SUBSTITUTE));
    }

    // ===== Inner types =====

    public record RenderedTemplate(String title, String body, int templateVersion) {}

    // Package-private for tests.
    record TemplateRow(String titleTemplate, String bodyTemplate, int templateVersion) {}
}
