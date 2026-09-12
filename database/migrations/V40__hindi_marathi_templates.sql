-- V40: Hindi and Marathi notification templates for all alert types.
--
-- Per Phase 8: adds hi (Hindi) and mr (Marathi) locale templates for all
-- existing English templates. The NotificationTemplateService falls back
-- to English if a template is not found in the user's preferred locale.
--
-- Template variables: {machineName}, {observedValue}, {observedUnit}, {message}
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.

-- ===== HINDI (hi) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- LOW_BATTERY
(NULL, 'LOW_BATTERY', 'OPEN', 'hi', 'बैटरी कम', 'मशीन {machineName} की बैटरी कम है: {observedValue}%', 1),
(NULL, 'LOW_BATTERY', 'RESOLVED', 'hi', 'बैटरी ठीक हुई', 'मशीन {machineName} की बैटरी स्तर ठीक हो गया है।', 1),
-- BATTERY_CRITICAL
(NULL, 'BATTERY_CRITICAL', 'OPEN', 'hi', 'गंभीर बैटरी चेतावनी', 'मशीन {machineName} की बैटरी गंभीर रूप से कम है: {observedValue}%। तुरंत ध्यान दें।', 1),
(NULL, 'BATTERY_CRITICAL', 'RESOLVED', 'hi', 'बैटरी ठीक हुई', 'मशीन {machineName} की बैटरी गंभीर स्थिति से ठीक हो गई है।', 1),
-- VOLTAGE_DROP
(NULL, 'VOLTAGE_DROP', 'OPEN', 'hi', 'वोल्टेज कम', 'मशीन {machineName} में वोल्टेज कम हुआ: {observedValue}V', 1),
(NULL, 'VOLTAGE_DROP', 'RESOLVED', 'hi', 'वोल्टेज ठीक हुआ', 'मशीन {machineName} का वोल्टेज ठीक हो गया है।', 1),
(NULL, 'VOLTAGE_DROP', 'ESCALATED', 'hi', 'वोल्टेज गंभीर — उन्नत', 'मशीन {machineName} का वोल्टेज गंभीर रूप से गिर गया: {observedValue}V। उन्नति ट्रिगर की गई।', 1),
-- GSM_SIGNAL_LOW
(NULL, 'GSM_SIGNAL_LOW', 'OPEN', 'hi', 'जीएसएम सिग्नल कम', 'मशीन {machineName} पर जीएसएम सिग्नल कमजोर है: {observedValue}', 1),
(NULL, 'GSM_SIGNAL_LOW', 'RESOLVED', 'hi', 'जीएसएम सिग्नल ठीक', 'मशीन {machineName} का जीएसएम सिग्नल ठीक हो गया है।', 1),
-- DEVICE_OFFLINE
(NULL, 'DEVICE_OFFLINE', 'OPEN', 'hi', 'डिवाइस ऑफलाइन', 'मशीन {machineName} का डिवाइस {observedValue} मिनट से ऑफलाइन है।', 1),
(NULL, 'DEVICE_OFFLINE', 'RESOLVED', 'hi', 'डिवाइस ऑनलाइन', 'मशीन {machineName} का डिवाइस वापस कनेक्ट हो गया है।', 1),
(NULL, 'DEVICE_OFFLINE', 'ESCALATED', 'hi', 'डिवाइस ऑफलाइन — उन्नत', 'मशीन {machineName} का डिवाइस लंबे समय से ऑफलाइन है ({observedValue} मिनट)। उन्नति ट्रिगर की गई।', 1),
-- DEVICE_ONLINE
(NULL, 'DEVICE_ONLINE', 'OPEN', 'hi', 'डिवाइस वापस ऑनलाइन', 'मशीन {machineName} का डिवाइस ऑफलाइन होने के बाद फिर से कनेक्ट हो गया है।', 1),
-- SIM_EXPIRY
(NULL, 'SIM_EXPIRY', 'OPEN', 'hi', 'सिम समाप्ति अनुस्मारक', 'मशीन {machineName} का सिम प्लान {observedValue} दिनों में समाप्त होगा।', 1),
(NULL, 'SIM_EXPIRY', 'RESOLVED', 'hi', 'सिम समाप्ति ठीक', 'मशीन {machineName} का सिम प्लान नवीनीकृत या समाप्त हो गया है।', 1),
-- SIM_EXPIRING
(NULL, 'SIM_EXPIRING', 'OPEN', 'hi', 'सिम जल्दी समाप्त', 'मशीन {machineName} का सिम प्लान {observedValue} दिनों में समाप्त होगा। कृपया नवीनीकरण करें।', 1),
-- SIM_EXPIRED
(NULL, 'SIM_EXPIRED', 'OPEN', 'hi', 'सिम समाप्त', 'मशीन {machineName} का सिम प्लान समाप्त हो गया है। कनेक्टिविटी बाधित हो सकती है।', 1),
(NULL, 'SIM_EXPIRED', 'RESOLVED', 'hi', 'सिम प्लान नवीनीकृत', 'मशीन {machineName} का सिम प्लान नवीनीकृत हो गया है।', 1),
-- EXTERNAL_POWER_LOW
(NULL, 'EXTERNAL_POWER_LOW', 'OPEN', 'hi', 'बाह्�ी शक्ति कम', 'मशीन {machineName} पर बाह्य शक्ति वोल्टेज कम है। कृपया पावर कनेक्शन जांचें।', 1),
(NULL, 'EXTERNAL_POWER_LOW', 'RESOLVED', 'hi', 'बाह्य शक्ति ठीक', 'मशीन {machineName} की बाह्य शक्ति ठीक हो गई है।', 1),
-- EXTERNAL_POWER_CUT
(NULL, 'EXTERNAL_POWER_CUT', 'OPEN', 'hi', 'बाह्य शक्ति कटी', 'मशीन {machineName} पर बाह्य शक्ति सुरक्षा सक्रिय हुई है। तुरंत बंद होगा — तुरंत ध्यान दें।', 1),
(NULL, 'EXTERNAL_POWER_CUT', 'RESOLVED', 'hi', 'बाह्य शक्ति बहाल', 'मशीन {machineName} की बाह्य शक्ति बहाल हो गई है।', 1),
-- LOW_POWER_SHUTDOWN
(NULL, 'LOW_POWER_SHUTDOWN', 'OPEN', 'hi', 'कम शक्ति बंद', 'मशीन {machineName} कम बैटरी के कारण बंद हो रही है। डिवाइस जल्द ऑफलाइन होगा।', 1),
(NULL, 'LOW_POWER_SHUTDOWN', 'RESOLVED', 'hi', 'डिवाइस ऑनलाइन', 'मशीन {machineName} कम-शक्ति बंद से ठीक होकर ऑनलाइन हो गई है।', 1),
-- INTERNAL_BATTERY_LOW
(NULL, 'INTERNAL_BATTERY_LOW', 'OPEN', 'hi', 'आंतरिक बैटरी कम', 'मशीन {machineName} की आंतरिक बैकअप बैटरी कम है। कृपया बैटरी चार्ज या बदलें।', 1),
(NULL, 'INTERNAL_BATTERY_LOW', 'RESOLVED', 'hi', 'आंतरिक बैटरी ठीक', 'मशीन {machineName} की आंतरिक बैकअप बैटरी ठीक हो गई है।', 1),
-- COMMAND_ACK
(NULL, 'COMMAND_ACK', 'ACK', 'hi', 'कमांड स्वीकृत', 'मशीन {machineName} ने रिले कमांड स्वीकार कर लिया है। पूर्ण होने की प्रतीक्षा में।', 1),
-- MACHINE_ON
(NULL, 'MACHINE_ON', 'DONE', 'hi', 'मशीन चालू हुई', 'मशीन {machineName} सफलतापूर्वक चालू कर दी गई है।', 1),
-- MACHINE_OFF
(NULL, 'MACHINE_OFF', 'DONE', 'hi', 'मशीन बंद हुई', 'मशीन {machineName} सफलतापूर्वक बंद कर दी गई है।', 1),
-- COMMAND_FAILED
(NULL, 'COMMAND_FAILED', 'FAILED', 'hi', 'कमांड विफल', 'मशीन {machineName} पर रिले कमांड विफल हुआ। त्रुटि: {message}। कृपया पुनः प्रयास करें या डिवाइस कनेक्टिविटी जांचें।', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;

-- ===== MARATHI (mr) templates =====
INSERT INTO notification_templates (organization_id, alert_type, incident_state, locale, title_template, body_template, template_version) VALUES
-- LOW_BATTERY
(NULL, 'LOW_BATTERY', 'OPEN', 'mr', 'बॅटरी कमी', 'मशीन {machineName} वर बॅटरी कमी आहे: {observedValue}%', 1),
(NULL, 'LOW_BATTERY', 'RESOLVED', 'mr', 'बॅटरी ठीक झाली', 'मशीन {machineName} वर बॅटरी पातळी ठीक झाली आहे.', 1),
-- BATTERY_CRITICAL
(NULL, 'BATTERY_CRITICAL', 'OPEN', 'mr', 'गंभीर बॅटरी सूचना', 'मशीन {machineName} वर बॅटरी गंभीरपणे कमी आहे: {observedValue}%. त्वरित लक्ष द्या.', 1),
(NULL, 'BATTERY_CRITICAL', 'RESOLVED', 'mr', 'बॅटरी ठीक झाली', 'मशीन {machineName} वर बॅटरी गंभीर स्थितीतून ठीक झाली आहे.', 1),
-- VOLTAGE_DROP
(NULL, 'VOLTAGE_DROP', 'OPEN', 'mr', 'व्होल्टेज कमी', 'मशीन {machineName} वर व्होल्टेज कमी झाला: {observedValue}V', 1),
(NULL, 'VOLTAGE_DROP', 'RESOLVED', 'mr', 'व्होल्टेज ठीक झाला', 'मशीन {machineName} वर व्होल्टेज ठीक झाला आहे.', 1),
(NULL, 'VOLTAGE_DROP', 'ESCALATED', 'mr', 'व्होल्टेज गंभीर — वाढ', 'मशीन {machineName} वर व्होल्टेज गंभीरपणे कमी झाला: {observedValue}V. वाढवण्याची प्रक्रिया सुरू झाली.', 1),
-- GSM_SIGNAL_LOW
(NULL, 'GSM_SIGNAL_LOW', 'OPEN', 'mr', 'जीएसएम सिग्नल कमी', 'मशीन {machineName} वर जीएसएम सिग्नल कमक्षत आहे: {observedValue}', 1),
(NULL, 'GSM_SIGNAL_LOW', 'RESOLVED', 'mr', 'जीएसएम सिग्नल ठीक', 'मशीन {machineName} वर जीएसएम सिग्नल ठीक झाला आहे.', 1),
-- DEVICE_OFFLINE
(NULL, 'DEVICE_OFFLINE', 'OPEN', 'mr', 'डिवाइस ऑफलाइन', 'मशीन {machineName} चा डिवाइस {observedValue} मिनिटांपासून ऑफलाइन आहे.', 1),
(NULL, 'DEVICE_OFFLINE', 'RESOLVED', 'mr', 'डिवाइस ऑनलाइन', 'मशीन {machineName} चा डिवाइस पुन्हा कनेक्ट झाला आहे.', 1),
(NULL, 'DEVICE_OFFLINE', 'ESCALATED', 'mr', 'डिवाइस ऑफलाइन — वाढ', 'मशीन {machineName} चा डिवाइस दीर्घ काळ ऑफलाइन आहे ({observedValue} मिनिटे). वाढवण्याची प्रक्रिया सुरू झाली.', 1),
-- DEVICE_ONLINE
(NULL, 'DEVICE_ONLINE', 'OPEN', 'mr', 'डिवाइस पुन्हा ऑनलाइन', 'मशीन {machineName} चा डिवाइस ऑफलाइन झाल्यानंतर पुन्हा कनेक्ट झाला आहे.', 1),
-- SIM_EXPIRY
(NULL, 'SIM_EXPIRY', 'OPEN', 'mr', 'सिम समाप्ती आठवण', 'मशीन {machineName} चा सिम प्लान {observedValue} दिवसांत समाप्त होईल.', 1),
(NULL, 'SIM_EXPIRY', 'RESOLVED', 'mr', 'सिम समाप्ती ठीक', 'मशीन {machineName} चा सिम प्लान नूतनीकरण केला गेला आहे.', 1),
-- SIM_EXPIRING
(NULL, 'SIM_EXPIRING', 'OPEN', 'mr', 'सिम लवकर समाप्त', 'मशीन {machineName} चा सिम प्लान {observedValue} दिवसांत समाप्त होईल. कृपया नूतनीकरण करा.', 1),
-- SIM_EXPIRED
(NULL, 'SIM_EXPIRED', 'OPEN', 'mr', 'सिम समाप्त झाला', 'मशीन {machineName} चा सिम प्लान समाप्त झाला आहे. कनेक्टिव्हिटी बिघडू शकते.', 1),
(NULL, 'SIM_EXPIRED', 'RESOLVED', 'mr', 'सिम प्लान नूतनीकरण', 'मशीन {machineName} चा सिम प्लान नूतनीकरण केला गेला आहे.', 1),
-- EXTERNAL_POWER_LOW
(NULL, 'EXTERNAL_POWER_LOW', 'OPEN', 'mr', 'बाह्य शक्ती कमी', 'मशीन {machineName} वर बाह्य शक्ती व्होल्टेज कमी आहे. कृपया पावर कनेक्शन तपासा.', 1),
(NULL, 'EXTERNAL_POWER_LOW', 'RESOLVED', 'mr', 'बाह्य शक्ती ठीक', 'मशीन {machineName} वर बाह्य शक्ती ठीक झाली आहे.', 1),
-- EXTERNAL_POWER_CUT
(NULL, 'EXTERNAL_POWER_CUT', 'OPEN', 'mr', 'बाह्य शक्ती खंडित', 'मशीन {machineName} वर बाह्य शक्ती सुरक्षा सक्रिय झाली आहे. त्वरित बंद होईल — त्वरित लक्ष द्या.', 1),
(NULL, 'EXTERNAL_POWER_CUT', 'RESOLVED', 'mr', 'बाह्य शक्ती पुनर्स्थापित', 'मशीन {machineName} वर बाह्य शक्ती पुनर्स्थापित झाली आहे.', 1),
-- LOW_POWER_SHUTDOWN
(NULL, 'LOW_POWER_SHUTDOWN', 'OPEN', 'mr', 'कमी शक्ती बंद', 'मशीन {machineName} कमी बॅटरीमुळे बंद होत आहे. डिवाइस लवकर ऑफलाइन होईल.', 1),
(NULL, 'LOW_POWER_SHUTDOWN', 'RESOLVED', 'mr', 'डिवाइस ऑनलाइन', 'मशीन {machineName} कमी-शक्ती बंदातून ठीक होऊन ऑनलाइन झाली आहे.', 1),
-- INTERNAL_BATTERY_LOW
(NULL, 'INTERNAL_BATTERY_LOW', 'OPEN', 'mr', 'आंतरिक बॅटरी कमी', 'मशीन {machineName} ची आंतरिक बॅकअप बॅटरी कमी आहे. कृपया बॅटरी चार्ज करा किंवा बदला.', 1),
(NULL, 'INTERNAL_BATTERY_LOW', 'RESOLVED', 'mr', 'आंतरिक बॅटरी ठीक', 'मशीन {machineName} ची आंतरिक बॅकअप बॅटरी ठीक झाली आहे.', 1),
-- COMMAND_ACK
(NULL, 'COMMAND_ACK', 'ACK', 'mr', 'कमांड स्वीकारली', 'मशीन {machineName} ने रिले कमांड स्वीकारली आहे. पूर्ण होण्याची वाट पाहत आहे.', 1),
-- MACHINE_ON
(NULL, 'MACHINE_ON', 'DONE', 'mr', 'मशीन चालू झाली', 'मशीन {machineName} यशस्वीरित्या चालू करण्यात आली आहे.', 1),
-- MACHINE_OFF
(NULL, 'MACHINE_OFF', 'DONE', 'mr', 'मशीन बंद झाली', 'मशीन {machineName} यशस्वीरित्या बंद करण्यात आली आहे.', 1),
-- COMMAND_FAILED
(NULL, 'COMMAND_FAILED', 'FAILED', 'mr', 'कमांड अयशस्वी', 'मशीन {machineName} वर रिले कमांड अयशस्वी झाला. त्रुटी: {message}. कृपया पुन्हा प्रयत्न करा किंवा डिवाइस कनेक्टिव्हिटी तपासा.', 1)
ON CONFLICT (alert_type, incident_state, locale, template_version) DO NOTHING;
