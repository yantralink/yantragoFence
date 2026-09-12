-- V36: Notification templates for relay command lifecycle events.
-- Adds templates for COMMAND_ACK, MACHINE_ON, MACHINE_OFF, COMMAND_FAILED
-- alert types generated when relay ON/OFF commands transition through
-- ACK, DONE, or FAILED states.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Uses the same INSERT pattern as V24/V32/V34 with ON CONFLICT DO NOTHING.

INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- COMMAND_ACK: device acknowledged the command
(NULL, 'COMMAND_ACK', 'ACK', 'en', 'Command Acknowledged', 'Machine {machineName} has acknowledged the relay command. Waiting for completion.', 1),
-- MACHINE_ON: relay ON command completed successfully
(NULL, 'MACHINE_ON', 'DONE', 'en', 'Machine Turned On', 'Machine {machineName} has been turned on successfully.', 1),
-- MACHINE_OFF: relay OFF command completed successfully
(NULL, 'MACHINE_OFF', 'DONE', 'en', 'Machine Turned Off', 'Machine {machineName} has been turned off successfully.', 1),
-- COMMAND_FAILED: command failed or timed out
(NULL, 'COMMAND_FAILED', 'FAILED', 'en', 'Command Failed', 'Relay command failed on machine {machineName}. Error: {observedValue}. Please retry or check device connectivity.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;
