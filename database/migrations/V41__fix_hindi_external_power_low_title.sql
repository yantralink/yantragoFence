-- V41: Fix corrupted Hindi character in EXTERNAL_POWER_LOW template title.
--
-- V40 line 41 had a corrupted Unicode character in the Hindi title for
-- EXTERNAL_POWER_LOW OPEN: 'बाह्�ी शक्ति कम' should be 'बाह्य शक्ति कम'.
-- This migration updates the already-applied production row.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

UPDATE notification_templates
SET title_template = 'बाह्य शक्ति कम',
    updated_at = now()
WHERE alert_type = 'EXTERNAL_POWER_LOW'
  AND incident_state = 'OPEN'
  AND locale = 'hi'
  AND template_version = 1;
