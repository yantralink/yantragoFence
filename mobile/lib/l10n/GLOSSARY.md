# Yantrago Mobile — Localization Glossary

Shared terminology for Hindi (hi) and Marathi (mr) translations.
Source of truth: `mobile/lib/l10n/app_en.arb` (269 messages).

> **Status: AI-generated draft.** All Hindi/Marathi strings in this project are
> machine-assisted drafts and are **PENDING NATIVE-SPEAKER REVIEW** before
> production exposure (see localization plan, section 2). Do not treat any
> entry below as natively verified.

## Core terms

| English term | Hindi | Marathi |
| --- | --- | --- |
| Machine | मशीन | मशीन |
| Fence / Fencing | फ़ेंस / फ़ेंसिंग | फेन्स / फेन्सिंग |
| Geofence | जियोफ़ेंस | जियोफेन्स |
| Battery | बैटरी | बॅटरी |
| Internal battery | आंतरिक बैटरी | अंतर्गत बॅटरी |
| Charging | चार्जिंग / चार्ज हो रही है | चार्जिंग / चार्ज होत आहे |
| Recharge | रिचार्ज | रिचार्ज |
| Voltage | वोल्टेज | व्होल्टेज |
| Signal | सिग्नल | सिग्नल |
| GSM Signal | GSM सिग्नल | GSM सिग्नल |
| Command | कमांड | कमांड |
| Alert | अलर्ट | अलर्ट |
| Notification | सूचना | सूचना |
| Telemetry | टेलीमेट्री | टेलिमेट्री |
| Dashboard | डैशबोर्ड | डॅशबोर्ड |
| Status | स्थिति | स्थिती |
| Fault | फ़ॉल्ट | फॉल्ट |
| Online / Offline | ऑनलाइन / ऑफ़लाइन | ऑनलाइन / ऑफलाइन |
| Device | डिवाइस | डिव्हाइस |
| External power | बाहरी बिजली | बाह्य वीज |
| Speed | स्पीड (धीमा/तेज़ phrasing) | वेग |

## Actions and UI verbs

| English term | Hindi | Marathi |
| --- | --- | --- |
| Turn ON / Turn OFF | चालू करें / बंद करें | चालू करा / बंद करा |
| Save / Cancel | सेव करें / रद्द करें | सेव्ह करा / रद्द करा |
| Retry / Try again | फिर से कोशिश करें / पुनः प्रयास करें | पुन्हा लोड करा / पुन्हा प्रयत्न करा |
| Refresh | रिफ़्रेश करें | रिफ्रेश करा |
| Apply (filters) | लागू करें | लागू करा |
| Login / Log out | लॉगिन करें / लॉग आउट करें | लॉगिन करा / लॉग आउट करा |
| Acknowledge | पुष्टि करें | पुष्टी करा |
| Mark as read | पढ़ा हुआ मार्क करें | वाचलेले म्हणून चिन्हांकित करा |
| View machine | मशीन देखें | मशीन पहा |
| History | इतिहास | इतिहास |
| Settings | सेटिंग्स | सेटिंग्ज |
| Language | भाषा | भाषा |
| Profile | प्रोफ़ाइल | प्रोफाइल |
| Organization | संगठन | संस्था |
| Assigned | असाइन (की गईं) | वाटप (केलेल्या) |

## Theft protection

| English term | Hindi | Marathi |
| --- | --- | --- |
| Theft Protection | चोरी-रोधी सुरक्षा | चोरीरोधक सुरक्षा |
| Not Protected | संरक्षित नहीं | संरक्षित नाही |
| Geofence Radius | जियोफ़ेंस त्रिज्या | जियोफेन्स त्रिज्या |
| Speed Alert | स्पीड अलर्ट | स्पीड अलर्ट |
| Movement | हलचल | हालचाल |
| Breach | उल्लंघन | उल्लंघन |

## Relative time

| English term | Hindi | Marathi |
| --- | --- | --- |
| Just now | अभी-अभी | आत्ताच |
| {n} min ago | {n} मिनट पहले | {n} मिनिटांपूर्वी |
| {n} h ago | {n} घंटे पहले | {n} तासांपूर्वी |
| {n} d ago | {n} दिन पहले | {n} दिवसांपूर्वी |

## Safety-critical terms — PENDING NATIVE REVIEW

These strings describe command lifecycle, device acknowledgement, and fault /
battery states. A mistranslation here can mislead an operator into believing a
command succeeded when it has not (or vice versa). **All wording below is an
AI-generated draft and MUST be reviewed by a native Hindi speaker and a native
Marathi speaker before production exposure.**

| English term | Hindi (draft) | Marathi (draft) | Review note |
| --- | --- | --- | --- |
| Command sent. Waiting for device acknowledgement... | कमांड भेज दी गई है। डिवाइस की पुष्टि का इंतज़ार हो रहा है… | कमांड पाठवली आहे. डिव्हाइसच्या पुष्टीची वाट पाहिली जात आहे… | Must never read as "successful" / "done". Verify tense conveys in-progress waiting. |
| Pending | लंबित | प्रलंबित | Confirm naturalness for non-technical operators. |
| Ack (short badge) | पुष्टि | पुष्टी | Short badge; confirm it reads as "confirmation" not "acceptance". |
| Awaiting ACK | पुष्टि का इंतज़ार | पुष्टी प्रतीक्षित | Formal register in Marathi; check farmer familiarity. |
| Acknowledged | पुष्टि प्राप्त | पुष्टी प्राप्त | Means "acknowledgement received", NOT "command executed". |
| Done / Completed | पूर्ण / पूर्ण हुआ | पूर्ण / पूर्ण झाले | Confirm it cannot be confused with "acknowledged". |
| Failed | विफल | अयशस्वी | — |
| Timed Out | समय समाप्त | वेळ संपली | Verify it clearly implies the command expired without ACK. |
| Fault | फ़ॉल्ट | फॉल्ट | Transliteration chosen over दोष/गंभीर दोष; verify comprehension. |
| Battery Low / Critical Low | बैटरी कम / बैटरी अत्यधिक कम | बॅटरी कमी / बॅटरी अत्यंत कमी | Confirm severity distinction between Low and Critical is clear. |
| Low Power Shutdown | कम बिजली पर शटडाउन | कमी वीजेवर शटडाउन | "शटडाउन" transliteration; consider native wording if reviewers prefer. |
| Device acknowledged the command | डिवाइस ने पुष्टि की | डिव्हाइसने पुष्टी केली | — |
| Fence ON/OFF · confirmed | फ़ेंस चालू/बंद · पुष्टि प्राप्त | फेन्स चालू/बंद · पुष्टी प्राप्त | "confirmed" = ACK received, not state guaranteed; verify. |

Additional review guidance:

- Transliterations (कमांड, फ़ॉल्ट, शटडाउन, असाइन, रिचार्ज) were chosen for
  consistency with common Indian field usage. Reviewers may prefer native
  equivalents where they are clearer to farmers/operators.
- Marathi uses वीज for (electrical) power while Hindi uses बिजली; both were
  kept consistent across all alert strings.
- Units (V, m, km/h, %), brand identifiers (YantraGO, YG, IMEI, SIM, SMS,
  GSM, ACC) and Latin digits are intentionally left untranslated.
