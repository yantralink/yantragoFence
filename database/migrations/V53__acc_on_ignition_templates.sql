-- V53: Notification templates for ACC_ON ignition alert type.
--
-- Ignition on/off state pair from BR05 alarm codes:
--   0xFE (ACC on)  -> ACC_ON / OPEN     -> "engine started" push
--   0xFF (ACC off) -> ACC_ON / RESOLVED -> "engine stopped" push
--
-- A single ACC_ON alert type is used (per DeviceEventConsumer.handleAccAlarm):
-- OPEN = ignition on, RESOLVED = ignition off. There is no ESCALATED state
-- for ignition — the device only reports on/off transitions.
--
-- Template variables: {machineName}, {observedValue}, {observedUnit}, {message}
-- Ignition events carry no numeric observed value, so only {machineName} is
-- used in the body. {observedValue}/{observedUnit} are omitted to avoid the
-- safe-substitute "-" leaking into user-visible text.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== ENGLISH (en) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'ACC_ON', 'OPEN', 'en', 'Ignition ON', 'Engine started on machine {machineName}.', 1),
(NULL, 'ACC_ON', 'RESOLVED', 'en', 'Ignition OFF', 'Engine stopped on machine {machineName}.', 1)
ON CONFLICT DO NOTHING;

-- ===== HINDI (hi) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'ACC_ON', 'OPEN', 'hi', 'इग्निशन ऑन', 'मशीन {machineName} का इंजन चालू हो गया है।', 1),
(NULL, 'ACC_ON', 'RESOLVED', 'hi', 'इग्निशन ऑफ', 'मशीन {machineName} का इंजन बंद हो गया है।', 1)
ON CONFLICT DO NOTHING;

-- ===== MARATHI (mr) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'ACC_ON', 'OPEN', 'mr', 'इग्निशन ऑन', 'मशीन {machineName} चा इंजिन सुरू झाला आहे.', 1),
(NULL, 'ACC_ON', 'RESOLVED', 'mr', 'इग्निशन ऑफ', 'मशीन {machineName} चा इंजिन बंद झाला आहे.', 1)
ON CONFLICT DO NOTHING;

-- ===== Default notification preferences for existing users =====
-- Add IN_APP and PUSH preferences for ACC_ON for all tenant users
-- (same pattern as V38/V42/V44 — skip super-admins with NULL organization_id)
INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'IN_APP', 'ACC_ON', true, true
FROM users u
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'PUSH', 'ACC_ON', true, true
FROM users u
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;
