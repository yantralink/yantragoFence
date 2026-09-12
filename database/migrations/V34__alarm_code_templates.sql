-- V34: Notification templates for BR05 alarm-code-driven alert types.
-- Phase 6: Adds templates for the 4 alert types mapped from BR05 alarm codes
-- in DeviceEventConsumer (0x0E, 0x0F, 0x15, 0x19).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Uses the same INSERT pattern as V24/V32 with ON CONFLICT DO NOTHING.

INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- EXTERNAL_POWER_LOW (alarm code 0x0E)
(NULL, 'EXTERNAL_POWER_LOW', 'OPEN', 'en', 'External Power Low', 'External power voltage is low on machine {machineName}. Please check the power connection.', 1),
(NULL, 'EXTERNAL_POWER_LOW', 'RESOLVED', 'en', 'External Power Recovered', 'External power has recovered on machine {machineName}.', 1),
-- EXTERNAL_POWER_CUT (alarm code 0x0F)
(NULL, 'EXTERNAL_POWER_CUT', 'OPEN', 'en', 'External Power Cut', 'External power protection has been triggered on machine {machineName}. Imminent shutdown — immediate attention required.', 1),
(NULL, 'EXTERNAL_POWER_CUT', 'RESOLVED', 'en', 'External Power Restored', 'External power has been restored on machine {machineName}.', 1),
-- LOW_POWER_SHUTDOWN (alarm code 0x15)
(NULL, 'LOW_POWER_SHUTDOWN', 'OPEN', 'en', 'Low Power Shutdown', 'Machine {machineName} is shutting down due to low battery. The device will go offline shortly.', 1),
(NULL, 'LOW_POWER_SHUTDOWN', 'RESOLVED', 'en', 'Device Back Online', 'Machine {machineName} has recovered from low-power shutdown and is back online.', 1),
-- INTERNAL_BATTERY_LOW (alarm code 0x19)
(NULL, 'INTERNAL_BATTERY_LOW', 'OPEN', 'en', 'Internal Battery Low', 'Internal backup battery is low on machine {machineName}. Please charge or replace the backup battery.', 1),
(NULL, 'INTERNAL_BATTERY_LOW', 'RESOLVED', 'en', 'Internal Battery Recovered', 'Internal backup battery has recovered on machine {machineName}.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;
