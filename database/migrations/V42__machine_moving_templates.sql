-- V42: Notification templates for MACHINE_MOVING alert type (Phase 9).
--
-- Speed-based theft detection: triggers when GPS speed exceeds a threshold.
-- Template variables: {machineName}, {observedValue}, {observedUnit}
-- {observedValue} = speed in km/h, {observedUnit} = "km/h"
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== ENGLISH (en) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'MACHINE_MOVING', 'OPEN', 'en', 'Machine Moving', 'Machine {machineName} is moving (speed: {observedValue} km/h). Possible theft — verify immediately.', 1),
(NULL, 'MACHINE_MOVING', 'RESOLVED', 'en', 'Machine Stopped', 'Machine {machineName} has stopped moving.', 1),
(NULL, 'MACHINE_MOVING', 'ESCALATED', 'en', 'Machine Moving — Escalated', 'Machine {machineName} has been moving for an extended period (speed: {observedValue} km/h). Escalation triggered.', 1)
ON CONFLICT DO NOTHING;

-- ===== HINDI (hi) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'MACHINE_MOVING', 'OPEN', 'hi', 'मशीन चल रही है', 'मशीन {machineName} चल रही है (गति: {observedValue} किमी/घं)। संभावित चोरी — तुरंत जांचें।', 1),
(NULL, 'MACHINE_MOVING', 'RESOLVED', 'hi', 'मशीन रुक गई', 'मशीन {machineName} चलना बंद कर दिया है।', 1),
(NULL, 'MACHINE_MOVING', 'ESCALATED', 'hi', 'मशीन चल रही है — उन्नत', 'मशीन {machineName} लंबे समय से चल रही है (गति: {observedValue} किमी/घं)। उन्नति ट्रिगर की गई।', 1)
ON CONFLICT DO NOTHING;

-- ===== MARATHI (mr) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'MACHINE_MOVING', 'OPEN', 'mr', 'मशीन हलत आहे', 'मशीन {machineName} हलत आहे (वेग: {observedValue} किमी/ता)। शक्य चोरी — त्वरित तपासा.', 1),
(NULL, 'MACHINE_MOVING', 'RESOLVED', 'mr', 'मशीन थांबले', 'मशीन {machineName} हलणे बंद झाले आहे.', 1),
(NULL, 'MACHINE_MOVING', 'ESCALATED', 'mr', 'मशीन हलत आहे — वाढ', 'मशीन {machineName} दीर्घ काळ हलत आहे (वेग: {observedValue} किमी/ता). वाढवण्याची प्रक्रिया सुरू झाली.', 1)
ON CONFLICT DO NOTHING;

-- ===== Default notification preferences for existing users =====
-- Add IN_APP and PUSH preferences for MACHINE_MOVING for all tenant users
-- (same pattern as V38 — skip super-admins with NULL organization_id)
INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'IN_APP', 'MACHINE_MOVING', true, true
FROM users u
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'PUSH', 'MACHINE_MOVING', true, true
FROM users u
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;
