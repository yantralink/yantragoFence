-- V56: Notification templates for the automatic fence ON/OFF scheduling
-- feature (docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md, Phase 3).
--
-- Alert types emitted by ScheduleExecutionService:
--   SCHEDULED_ON_EXECUTED      / DONE    — scheduled ON confirmed by device
--   SCHEDULED_OFF_EXECUTED     / DONE    — scheduled OFF confirmed by device
--   SCHEDULED_COMMAND_FAILED   / FAILED  — scheduled command terminally failed
--                                          (device unreachable / dispatch error)
--   SCHEDULED_COMMAND_MISSED   / MISSED  — scheduled ON slot was too stale to
--                                          fire safely (never fired late)
--
-- Template variables: {machineName}, {observedUnit}
-- {observedUnit} carries the command direction ("ON"/"OFF") — it is the only
-- free-form String slot on AlertTransitionMessage (observedValue is Double).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== ENGLISH (en) =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'SCHEDULED_ON_EXECUTED',    'DONE',   'en', 'Machine Turned On (Scheduled)',  'Machine {machineName} was turned ON automatically by schedule.', 1),
(NULL, 'SCHEDULED_OFF_EXECUTED',   'DONE',   'en', 'Machine Turned Off (Scheduled)', 'Machine {machineName} was turned OFF automatically by schedule.', 1),
(NULL, 'SCHEDULED_COMMAND_FAILED', 'FAILED', 'en', 'Scheduled Command Failed',       'Scheduled {observedUnit} failed on machine {machineName} — the device could not be reached. Fence state is UNCHANGED. Please check the device.', 1),
(NULL, 'SCHEDULED_COMMAND_MISSED', 'MISSED', 'en', 'Scheduled Command Missed',       'The scheduled {observedUnit} for machine {machineName} was missed and was NOT executed for safety. Please check and turn it on manually if needed.', 1),
-- Defensive: no current code path emits TIMEOUT for this type (terminal run
-- failures always emit FAILED), but if a future path does, recipients get a
-- localized message instead of the raw "TYPE — STATE" fallback title.
(NULL, 'SCHEDULED_COMMAND_FAILED', 'TIMEOUT', 'en', 'Scheduled Command Timed Out',   'Scheduled {observedUnit} timed out on machine {machineName} — the device did not respond. Fence state is UNCHANGED. Please check the device.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;

-- ===== HINDI (hi) =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'SCHEDULED_ON_EXECUTED',    'DONE',   'hi', 'मशीन चालू हुई (शेड्यूल)',   'मशीन {machineName} शेड्यूल के अनुसार अपने आप चालू हो गई है।', 1),
(NULL, 'SCHEDULED_OFF_EXECUTED',   'DONE',   'hi', 'मशीन बंद हुई (शेड्यूल)',    'मशीन {machineName} शेड्यूल के अनुसार अपने आप बंद हो गई है।', 1),
(NULL, 'SCHEDULED_COMMAND_FAILED', 'FAILED', 'hi', 'शेड्यूल कमांड विफल',        'मशीन {machineName} पर शेड्यूल किया गया {observedUnit} विफल रहा — डिवाइस तक पहुंच नहीं हो सकी। फेंस की स्थिति अपरिवर्तित है। कृपया डिवाइस जांचें।', 1),
(NULL, 'SCHEDULED_COMMAND_MISSED', 'MISSED', 'hi', 'शेड्यूल कमांड छूट गया',     'मशीन {machineName} के लिए शेड्यूल किया गया {observedUnit} छूट गया और सुरक्षा कारणों से निष्पादित नहीं किया गया। कृपया जांचें और आवश्यकता होने पर मैन्युअल रूप से चालू करें।', 1),
(NULL, 'SCHEDULED_COMMAND_FAILED', 'TIMEOUT', 'hi', 'शेड्यूल कमांड समय समाप्त',   'मशीन {machineName} पर शेड्यूल किया गया {observedUnit} समय समाप्त हुआ — डिवाइस ने जवाब नहीं दिया। फेंस की स्थिति अपरिवर्तित है। कृपया डिवाइस जांचें।', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;

-- ===== MARATHI (mr) =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
(NULL, 'SCHEDULED_ON_EXECUTED',    'DONE',   'mr', 'मशीन चालू झाली (शेड्यूल)',   'मशीन {machineName} शेड्यूलनुसार आपोआप चालू झाली आहे.', 1),
(NULL, 'SCHEDULED_OFF_EXECUTED',   'DONE',   'mr', 'मशीन बंद झाली (शेड्यूल)',    'मशीन {machineName} शेड्यूलनुसार आपोआप बंद झाली आहे.', 1),
(NULL, 'SCHEDULED_COMMAND_FAILED', 'FAILED', 'mr', 'शेड्यूल कमांड अयशस्वी',      'मशीन {machineName} वर शेड्यूल केलेले {observedUnit} अयशस्वी झाले — डिवाइसशी संपर्क झाला नाही. फेंसची स्थिती बदललेली नाही. कृपया डिवाइस तपासा.', 1),
(NULL, 'SCHEDULED_COMMAND_MISSED', 'MISSED', 'mr', 'शेड्यूल कमांड चूकले',        'मशीन {machineName} साठी शेड्यूल केलेले {observedUnit} चूकले आणि सुरक्षेच्या कारणास्तव अंमलात आणले गेले नाही. कृपया तपासा आणि आवश्यकतेनुसार स्वतः चालू करा.', 1),
(NULL, 'SCHEDULED_COMMAND_FAILED', 'TIMEOUT', 'mr', 'शेड्यूल कमांड वेळ संपली',     'मशीन {machineName} वर शेड्यूल केलेले {observedUnit} वेळेत अंमलात आले नाही — डिवाइसने प्रतिसाद दिला नाही. फेंसची स्थिती बदललेली नाही. कृपया डिवाइस तपासा.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;

-- ===== Default notification preferences for existing users =====
-- Same pattern as V38/V42 — IN_APP + PUSH rows for each new alert type,
-- for all tenant users (skip super-admins with NULL organization_id).
INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'IN_APP', t.alert_type, true, true
FROM users u
CROSS JOIN (VALUES
    ('SCHEDULED_ON_EXECUTED'),
    ('SCHEDULED_OFF_EXECUTED'),
    ('SCHEDULED_COMMAND_FAILED'),
    ('SCHEDULED_COMMAND_MISSED')
) AS t(alert_type)
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, is_enabled, push_enabled)
SELECT u.id, u.organization_id, 'PUSH', t.alert_type, true, true
FROM users u
CROSS JOIN (VALUES
    ('SCHEDULED_ON_EXECUTED'),
    ('SCHEDULED_OFF_EXECUTED'),
    ('SCHEDULED_COMMAND_FAILED'),
    ('SCHEDULED_COMMAND_MISSED')
) AS t(alert_type)
WHERE u.organization_id IS NOT NULL
ON CONFLICT DO NOTHING;
