-- V52: Complete the notification template catalog (Multilingual Plan Phase 6).
--
-- Closes two catalog gaps found in the Phase 6 audit:
-- 1) COMMAND_FAILED / TIMEOUT existed in English only (V48) — Hindi and
--    Marathi recipients silently received English text for command timeouts.
-- 2) ESCALATED variants were missing entirely for LOW_BATTERY,
--    BATTERY_CRITICAL and GSM_SIGNAL_LOW (all locales). If the escalation
--    scheduler ever fires for those types, the consumer falls back to the
--    raw "TYPE — STATE" string; these rows make that path unreachable.
--
-- Native-speaker review of hi/mr wording is recorded as pending in the
-- multilingual plan (section 2 / GLOSSARY.md).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- COMMAND_FAILED + TIMEOUT: command timed out waiting for device response (hi/mr)
(NULL, 'COMMAND_FAILED', 'TIMEOUT', 'hi', 'कमांड का समय समाप्त', 'मशीन {machineName} पर रिले कमांड का समय समाप्त हो गया। {message}। कृपया डिवाइस कनेक्टिविटी जांचें और पुनः प्रयास करें।', 1),
(NULL, 'COMMAND_FAILED', 'TIMEOUT', 'mr', 'कमांडची वेळ संपली', 'मशीन {machineName} वर रिले कमांडची वेळ संपली. {message}. कृपया डिवाइस कनेक्टिव्हिटी तपासा आणि पुन्हा प्रयत्न करा.', 1),
-- LOW_BATTERY escalated (extended low-battery condition)
(NULL, 'LOW_BATTERY', 'ESCALATED', 'en', 'Low Battery — Escalated', 'Battery on machine {machineName} remains critically low ({observedValue}%). Escalation triggered.', 1),
(NULL, 'LOW_BATTERY', 'ESCALATED', 'hi', 'बैटरी कम — एस्कलेटेड', 'मशीन {machineName} पर बैटरी लंबे समय से अत्यधिक कम है ({observedValue}%)। एस्कलेशन ट्रिगर हुआ।', 1),
(NULL, 'LOW_BATTERY', 'ESCALATED', 'mr', 'बॅटरी कमी — एस्कलेटेड', 'मशीन {machineName} वर बॅटरी बराच वेळ अत्यंत कमी आहे ({observedValue}%). एस्कलेशन ट्रिगर झाले.', 1),
-- BATTERY_CRITICAL escalated
(NULL, 'BATTERY_CRITICAL', 'ESCALATED', 'en', 'Critical Battery — Escalated', 'Battery on machine {machineName} has stayed critically low ({observedValue}%). Escalation triggered.', 1),
(NULL, 'BATTERY_CRITICAL', 'ESCALATED', 'hi', 'गंभीर बैटरी — एस्कलेटेड', 'मशीन {machineName} पर बैटरी लंबे समय से गंभीर रूप से कम है ({observedValue}%)। एस्कलेशन ट्रिगर हुआ।', 1),
(NULL, 'BATTERY_CRITICAL', 'ESCALATED', 'mr', 'गंभीर बॅटरी — एस्कलेटेड', 'मशीन {machineName} वर बॅटरी बराच वेळ गंभीरपणे कमी आहे ({observedValue}%). एस्कलेशन ट्रिगर झाले.', 1),
-- GSM_SIGNAL_LOW escalated
(NULL, 'GSM_SIGNAL_LOW', 'ESCALATED', 'en', 'Low GSM Signal — Escalated', 'GSM signal on machine {machineName} remains weak ({observedValue}). Escalation triggered.', 1),
(NULL, 'GSM_SIGNAL_LOW', 'ESCALATED', 'hi', 'कम GSM सिग्नल — एस्कलेटेड', 'मशीन {machineName} पर GSM सिग्नल लंबे समय से कमज़ोर है ({observedValue})। एस्कलेशन ट्रिगर हुआ।', 1),
(NULL, 'GSM_SIGNAL_LOW', 'ESCALATED', 'mr', 'कमी GSM सिग्नल — एस्कलेटेड', 'मशीन {machineName} वर GSM सिग्नल बराच वेळ कमजोर आहे ({observedValue}). एस्कलेशन ट्रिगर झाले.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;
