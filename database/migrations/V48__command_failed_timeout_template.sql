-- V48: Add COMMAND_FAILED template for TIMEOUT incident state.
--
-- Phase 2 added TIMEOUT as a terminal command state. When a command times out,
-- CommandResultConsumer generates a COMMAND_FAILED alert with incidentState=TIMEOUT.
-- V36/V37 only seeded a template for incident_state=FAILED, so TIMEOUT notifications
-- fell back to the raw "COMMAND_FAILED — TIMEOUT" title.
--
-- This migration adds a proper template for the TIMEOUT incident state.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- COMMAND_FAILED + TIMEOUT: command timed out waiting for device response
(NULL, 'COMMAND_FAILED', 'TIMEOUT', 'en', 'Command Timed Out', 'Relay command timed out on machine {machineName}. {message}. Please check device connectivity and retry.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;
