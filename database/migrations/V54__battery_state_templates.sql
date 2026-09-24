-- V54: Notification templates for battery-state alert types.
--
-- The internal battery percentage encodes two states, matching the app's
-- Charging Status / Fence Fault tiles (BatteryStateAlertService):
--   10%  -> MACHINE_CHARGING / OPEN -> "Machine is Charging" push
--   100% -> FENCE_FAULT      / OPEN -> "Fault Detected" push
--
-- OPEN templates only. Incident resolutions are silent by design
-- (CanonicalAlertService.resolveIncidentSilently writes no outbox event),
-- so no RESOLVED templates exist — users only ever see the two entry
-- notifications, one per state transition.
--
-- Template variables: {machineName}, {observedValue}, {observedUnit}, {message}
-- Only {machineName} is used — the battery level is implicit in the alert type.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== ENGLISH (en) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'MACHINE_CHARGING', 'OPEN', 'en', 'Machine is Charging', 'Machine {machineName} is now charging.', 1),
(NULL, 'FENCE_FAULT', 'OPEN', 'en', 'Fault Detected', 'Fault detected on machine {machineName}.', 1)
ON CONFLICT DO NOTHING;

-- ===== HINDI (hi) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'MACHINE_CHARGING', 'OPEN', 'hi', 'मशीन चार्ज हो रही है', 'मशीन {machineName} अब चार्ज हो रही है।', 1),
(NULL, 'FENCE_FAULT', 'OPEN', 'hi', 'फॉल्ट मिला', 'मशीन {machineName} में फॉल्ट मिला है।', 1)
ON CONFLICT DO NOTHING;

-- ===== MARATHI (mr) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'MACHINE_CHARGING', 'OPEN', 'mr', 'मशीन चार्ज होत आहे', 'मशीन {machineName} आता चार्ज होत आहे.', 1),
(NULL, 'FENCE_FAULT', 'OPEN', 'mr', 'फॉल्ट आढळला', 'मशीन {machineName} मध्ये फॉल्ट आढळला आहे.', 1)
ON CONFLICT DO NOTHING;

-- ===== Default notification preferences for existing users =====
-- Add IN_APP and PUSH preferences for both types for all tenant users
-- (same pattern as V42/V44/V53 — skip super-admins with NULL organization_id)
INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'IN_APP', t.alert_type, true, true
FROM users u
CROSS JOIN (VALUES ('MACHINE_CHARGING'), ('FENCE_FAULT')) AS t(alert_type)
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'PUSH', t.alert_type, true, true
FROM users u
CROSS JOIN (VALUES ('MACHINE_CHARGING'), ('FENCE_FAULT')) AS t(alert_type)
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;
