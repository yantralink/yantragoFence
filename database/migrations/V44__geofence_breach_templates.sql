-- V44: Notification templates for GEOFENCE_BREACH alert type (Phase 10).
--
-- Geo-fence breach detection: triggers when a machine moves outside its
-- defined geo-fence boundary. Auto-resolves when the machine returns inside.
-- Template variables: {machineName}, {observedValue}, {observedUnit}
-- {observedValue} = distance from center in meters, {observedUnit} = "meters"
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== ENGLISH (en) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'GEOFENCE_BREACH', 'OPEN', 'en', 'Geo-Fence Breach', 'Machine {machineName} has moved {observedValue}m outside its geo-fence. Possible theft — verify immediately.', 1),
(NULL, 'GEOFENCE_BREACH', 'RESOLVED', 'en', 'Machine Back in Zone', 'Machine {machineName} has returned within its geo-fence boundary.', 1),
(NULL, 'GEOFENCE_BREACH', 'ESCALATED', 'en', 'Geo-Fence Breach — Escalated', 'Machine {machineName} has been outside its geo-fence for an extended period ({observedValue}m). Escalation triggered.', 1)
ON CONFLICT DO NOTHING;

-- ===== HINDI (hi) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'GEOFENCE_BREACH', 'OPEN', 'hi', 'जियो-फेंस उल्लंघन', 'मशीन {machineName} अपनी जियो-फेंस सीमा से {observedValue}मी बाहर चली गई है। संभावित चोरी — तुरंत जांचें।', 1),
(NULL, 'GEOFENCE_BREACH', 'RESOLVED', 'hi', 'मशीन वापस क्षेत्र में', 'मशीन {machineName} अपनी जियो-फेंस सीमा के भीतर वापस आ गई है।', 1),
(NULL, 'GEOFENCE_BREACH', 'ESCALATED', 'hi', 'जियो-फेंस उल्लंघन — उन्नत', 'मशीन {machineName} लंबे समय से अपनी जियो-फेंस के बाहर है ({observedValue}मी)। उन्नति ट्रिगर की गई।', 1)
ON CONFLICT DO NOTHING;

-- ===== MARATHI (mr) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'GEOFENCE_BREACH', 'OPEN', 'mr', 'जिओ-फेन्स उल्लंघन', 'मशीन {machineName} त्याच्या जिओ-फेन्स सीमेबाहेर {observedValue}मी गेली आहे. शक्य चोरी — त्वरित तपासा.', 1),
(NULL, 'GEOFENCE_BREACH', 'RESOLVED', 'mr', 'मशीन परत क्षेत्रात', 'मशीन {machineName} त्याच्या जिओ-फेन्स सीमेत परत आली आहे.', 1),
(NULL, 'GEOFENCE_BREACH', 'ESCALATED', 'mr', 'जिओ-फेन्स उल्लंघन — वाढ', 'मशीन {machineName} दीर्घ काळ जिओ-फेन्स बाहेर आहे ({observedValue}मी). वाढवण्याची प्रक्रिया सुरू झाली.', 1)
ON CONFLICT DO NOTHING;

-- ===== Default notification preferences for existing users =====
-- Add IN_APP and PUSH preferences for GEOFENCE_BREACH for all tenant users
INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'IN_APP', 'GEOFENCE_BREACH', true, true
FROM users u
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'PUSH', 'GEOFENCE_BREACH', true, true
FROM users u
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;
