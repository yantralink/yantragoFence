-- V37: Fix COMMAND_FAILED template to use {message} instead of {observedValue}.
-- The CommandResultConsumer passes the error detail in the message field
-- (not observedValue, which is a Double and cannot hold a string error).
-- The NotificationEventConsumer now exposes {message} as a template variable.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

UPDATE notification_templates
SET body_template = 'Relay command failed on machine {machineName}. Error: {message}. Please retry or check device connectivity.'
WHERE alert_type = 'COMMAND_FAILED'
  AND incident_state = 'FAILED'
  AND locale = 'en'
  AND template_version = 1;
