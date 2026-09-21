package com.yantrago.api.service;

import com.yantrago.api.dto.notification.EventCatalogDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * CI gate for the notification template catalog (Multilingual Plan Phase 6).
 *
 * Runs WITHOUT a database: parses every notification_templates INSERT seeded
 * by the Flyway migrations and asserts:
 *  1. Every enabled event type from {@link NotificationPreferenceService#getEventCatalog()}
 *     has at least one active template.
 *  2. Every seeded (alert_type, incident_state) pair is available in ALL
 *     supported locales (en, hi, mr) — a locale gap must fail the build, not
 *     silently serve English fallback to Hindi/Marathi users.
 *  3. Every seeded locale is a supported code and every title/body is
 *     non-blank.
 *
 * Any new migration that widens the catalog or adds a state must pass this
 * gate — V40-style reactive fixes (adding hi/mr after the fact) are exactly
 * what this test exists to prevent.
 */
class NotificationTemplateCatalogTest {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "hi", "mr");

    /** (alert_type, incident_state) → locales seeded across all migrations. */
    private static final Map<String, Set<String>> catalog = new HashMap<>();
    /** Seeded locale codes as written in the migrations (for typo detection). */
    private static final Set<String> seededLocales = new java.util.HashSet<>();
    private static final List<String> blankTextRows = new ArrayList<>();

    @BeforeAll
    static void parseMigrations() throws IOException {
        Path migrationDir = locateMigrationDir();
        try (Stream<Path> files = Files.list(migrationDir)) {
            files.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .forEach(NotificationTemplateCatalogTest::parseFile);
        }
    }

    @Test
    @DisplayName("every enabled event type has at least one template")
    void everyEnabledEventTypeHasTemplates() {
        NotificationPreferenceService service = Mockito.mock(NotificationPreferenceService.class);
        when(service.getEventCatalog()).thenCallRealMethod();
        List<EventCatalogDto> enabled = service.getEventCatalog();

        List<String> missing = enabled.stream()
                .filter(dto -> catalog.keySet().stream()
                        .noneMatch(key -> key.startsWith(dto.eventType() + " / ")))
                .map(EventCatalogDto::eventType)
                .toList();

        assertTrue(missing.isEmpty(),
                "Enabled event types without any notification template: " + missing);
    }

    @Test
    @DisplayName("every seeded (type, state) pair exists in en, hi and mr")
    void everySeededPairIsAvailableInAllLocales() {
        List<String> gaps = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : catalog.entrySet()) {
            for (String locale : SUPPORTED_LOCALES) {
                if (!entry.getValue().contains(locale)) {
                    gaps.add(entry.getKey() + " missing '" + locale + "'");
                }
            }
        }
        assertFalse(gaps.isEmpty() && catalog.isEmpty(), "No templates parsed — parser or path broken");
        assertTrue(gaps.isEmpty(),
                "Incomplete template catalog (recipients would silently receive English fallback): "
                        + String.join("; ", gaps));
    }

    @Test
    @DisplayName("all seeded locales are supported codes and text is non-blank")
    void seededLocalesAreSupportedAndTextPresent() {
        Set<String> unsupported = new java.util.HashSet<>(seededLocales);
        unsupported.removeAll(SUPPORTED_LOCALES);
        assertTrue(unsupported.isEmpty(),
                "Templates seeded with unsupported locale codes: " + unsupported);
        assertTrue(blankTextRows.isEmpty(),
                "Templates with blank title/body: " + blankTextRows);
    }

    // ===== SQL parsing =====

    private static void parseFile(Path file) {
        String sql;
        try {
            sql = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read migration " + file, e);
        }
        sql = stripLineComments(sql);
        Matcher stmt = Pattern.compile(
                        "INSERT\\s+INTO\\s+notification_templates[^;]*?VALUES\\s*(.*?);",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(sql);
        while (stmt.find()) {
            // Drop the trailing ON CONFLICT (...) DO NOTHING clause — its
            // parenthesized conflict-target column list would otherwise be
            // mistaken for a tuple.
            String valuesBody = stmt.group(1).replaceAll("(?i)\\s*ON\\s+CONFLICT\\s+.*$", "");
            for (List<String> tuple : normalize(parseTuples(valuesBody))) {
                // Columns: organization_id, alert_type, incident_state, locale,
                //          title_template, body_template, template_version
                if (tuple.size() != 7) {
                    throw new IllegalStateException("Unexpected tuple arity in " + file + ": " + tuple);
                }
                String type = tuple.get(1);
                String state = tuple.get(2);
                String locale = tuple.get(3);
                String title = tuple.get(4);
                String body = tuple.get(5);
                seededLocales.add(locale);
                catalog.computeIfAbsent(type + " / " + state, k -> new java.util.HashSet<>()).add(locale);
                if (title.isBlank() || body.isBlank()) {
                    blankTextRows.add(file.getFileName() + ": " + type + "/" + state + "/" + locale);
                }
            }
        }
    }

    /** Splits a VALUES body into tuples (raw parenthesized groups), honoring '' escapes. */
    private static List<List<String>> parseTuples(String body) {
        List<List<String>> tuples = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\'') {
                    if (i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                        i++; // escaped quote inside string
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
            } else if (c == '(') {
                depth++;
                if (depth == 1) start = i + 1;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    tuples.add(splitTopLevel(body.substring(start, i)));
                    start = -1;
                }
            }
        }
        return tuples;
    }

    /** Splits one tuple body on top-level commas (outside single-quoted strings). */
    private static List<String> splitTopLevel(String s) {
        List<String> values = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                buf.append(c);
                if (c == '\'') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        buf.append('\'');
                        i++;
                    } else {
                        inString = false;
                    }
                }
            } else if (c == '\'') {
                inString = true;
                buf.append(c);
            } else if (c == ',') {
                values.add(buf.toString().trim());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        values.add(buf.toString().trim());
        return values;
    }

    private static List<List<String>> normalize(List<List<String>> tuples) {
        List<List<String>> out = new ArrayList<>();
        for (List<String> tuple : tuples) {
            List<String> values = new ArrayList<>();
            for (String v : tuple) values.add(unquote(v));
            out.add(values);
        }
        return out;
    }

    private static String unquote(String v) {
        String t = v.trim();
        if (t.startsWith("'") && t.endsWith("'") && t.length() >= 2) {
            return t.substring(1, t.length() - 1).replace("''", "'");
        }
        return t;
    }

    /** Removes -- line comments (outside string literals) so comment prose is never parsed. */
    private static String stripLineComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        out.append('\'');
                        i++;
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                out.append('\n');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static Path locateMigrationDir() {
        // Works when tests run from backend/ (Gradle default) or repo root.
        for (Path candidate : List.of(
                Paths.get("src/main/resources/db/migration"),
                Paths.get("backend/src/main/resources/db/migration"))) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot locate db/migration directory — run from backend/ or repo root");
    }
}
