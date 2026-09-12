-- V32__notification_templates_complete.sql
-- SIG 13: Complete template coverage for all alert types and incident states.
-- Adds templates missing from V24: BATTERY_CRITICAL, DEVICE_ONLINE, DEVICE_OFFLINE (ESCALATED),
-- SIM_EXPIRING, SIM_EXPIRED, VOLTAGE_DROP (ESCALATED).
-- Uses the same INSERT pattern as V24 with ON CONFLICT DO NOTHING.

INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- BATTERY_CRITICAL
(NULL, 'BATTERY_CRITICAL', 'OPEN', 'en', 'Critical Battery Alert', 'Battery is critically low on machine {machineName}: {observedValue}%. Immediate attention required.', 1),
(NULL, 'BATTERY_CRITICAL', 'RESOLVED', 'en', 'Critical Battery Recovered', 'Battery level has recovered from critical on machine {machineName}.', 1),
-- DEVICE_ONLINE (recovery notification)
(NULL, 'DEVICE_ONLINE', 'OPEN', 'en', 'Device Back Online', 'Device for machine {machineName} has reconnected after being offline.', 1),
-- DEVICE_OFFLINE (ESCALATED — offline for extended period)
(NULL, 'DEVICE_OFFLINE', 'ESCALATED', 'en', 'Device Offline — Escalated', 'Device for machine {machineName} has been offline for an extended period ({observedValue} minutes). Escalation triggered.', 1),
-- SIM_EXPIRING (30/14/7 day warnings)
(NULL, 'SIM_EXPIRING', 'OPEN', 'en', 'SIM Expiring Soon', 'SIM plan for machine {machineName} expires in {observedValue} days. Please renew to avoid service interruption.', 1),
-- SIM_EXPIRED
(NULL, 'SIM_EXPIRED', 'OPEN', 'en', 'SIM Expired', 'SIM plan for machine {machineName} has expired. Connectivity may be lost.', 1),
(NULL, 'SIM_EXPIRED', 'RESOLVED', 'en', 'SIM Plan Renewed', 'SIM plan for machine {machineName} has been renewed.', 1),
-- VOLTAGE_DROP (ESCALATED)
(NULL, 'VOLTAGE_DROP', 'ESCALATED', 'en', 'Voltage Drop — Escalated', 'Voltage on machine {machineName} has dropped critically to {observedValue}V. Escalation triggered.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;
