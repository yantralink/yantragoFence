# YantraGO Notification System — Manual Test Cases

> **Purpose:** End-to-end manual verification of the notification functionality across backend, mobile, and infrastructure layers.
>
> **Audience:** QA engineers, developers performing pre-deployment validation, and staging operators.
>
> **Scope:** Alert generation → outbox → RabbitMQ → notification inbox → WebSocket → push delivery → mobile UI. Covers all six notification phases.

---

## Table of Contents

1. [Test Environment Prerequisites](#1-test-environment-prerequisites)
2. [Test Data Setup](#2-test-data-setup)
3. [Phase 1 — Reliable Alert Foundation](#3-phase-1--reliable-alert-foundation)
4. [Phase 2 — Rule Evaluation, Offline & Expiry Detection](#4-phase-2--rule-evaluation-offline--expiry-detection)
5. [Phase 3 — Recipient Inbox, Preferences & APIs](#5-phase-3--recipient-inbox-preferences--apis)
6. [Phase 4 — Mobile Inbox & Alert Experience](#6-phase-4--mobile-inbox--alert-experience)
7. [Phase 5 — FCM Push Delivery](#7-phase-5--fcm-push-delivery)
8. [Phase 6 — Production Rollout & Operations](#8-phase-6--production-rollout--operations)
9. [Cross-Cutting Security Tests](#9-cross-cutting-security-tests)
10. [Edge Cases & Failure Scenarios](#10-edge-cases--failure-scenarios)
11. [Test Execution Summary Template](#11-test-execution-summary-template)

---

## 1. Test Environment Prerequisites

### 1.1 Infrastructure

| Component | Requirement |
|-----------|-------------|
| PostgreSQL 15 + PostGIS | Running, migrations V1–V32 applied |
| RabbitMQ 3 | Running with management plugin (port 15672) |
| Redis 7 | Running |
| Backend API | Running on `http://localhost:8080` |
| Mobile app | Running on Android emulator or physical device |
| Firebase project | Created, `FCM_SERVICE_ACCOUNT` env var set, server key available |
| Push token encryption key | `PUSH_TOKEN_ENCRYPTION_KEY` env var set (32-byte AES-GCM key) |

### 1.2 Configuration Verification

Before starting tests, verify these configuration values:

```yaml
# application.yml — verify or override via env vars
push.provider: fcm                          # must be "fcm" for push tests
notification.push.enabled: true             # must be true for push tests
websocket.allowed-origins: http://localhost:8081  # mobile app origin
alert.offline.grace-minutes: 15
alert.offline.batch-size: 100
alert.expiry.milestones-days: 7,3,1,0
```

### 1.3 Tools Needed

- **Postman or curl** — for REST API calls
- **RabbitMQ Management UI** (`http://localhost:15672`) — to inspect queues, exchanges, DLQ
- **psql** or **pgAdmin** — to inspect database tables
- **Firebase Console** — to send test push messages and verify delivery
- **Android Studio** — for mobile app, logcat, and emulator
- **STOMP client** (e.g. `websocat` or browser console) — for WebSocket tests

---

## 2. Test Data Setup

### 2.1 Create Test Organization & Users

```
Prerequisite: An admin user must exist for each test organization.

Action:
1. Register two organizations (Org A, Org B) via the admin API.
2. In each org, create:
   - 1 admin user (role: ADMIN)
   - 1 operator user (role: OPERATOR)
   - 1 customer user (role: CUSTOMER) linked to a customer record
3. In Org A, register 2 machines (Machine-A1, Machine-A2).
4. In Org B, register 1 machine (Machine-B1).
5. Assign Machine-A1 to the Org A customer.
6. Do NOT assign Machine-A2 to anyone.
7. Assign Machine-B1 to the Org B customer.

Expected: Two isolated tenants with machines and customer assignments.
```

### 2.2 Register Device Tokens (for push tests)

```
Action:
1. Log into the mobile app as Org A customer.
2. The app auto-registers the FCM token via POST /api/v1/notifications/device-tokens
3. Repeat for Org B customer.

Verify in DB:
  SELECT id, user_id, token_fingerprint, platform, is_active 
  FROM user_device_tokens 
  WHERE is_active = true;

Expected: Two active tokens, one per user. token_fingerprint is NOT null 
          (confirms encryption at rest). The raw FCM token is NOT stored.
```

---

## 3. Phase 1 — Reliable Alert Foundation

### TC-1.1: Outbox Row Created on Alert Transition

```
ID:          TC-1.1
Phase:       1
Priority:    Critical
Objective:   Verify that an alert transition creates an event_outbox row
Precondition: Machine-A1 is online and reporting telemetry

Steps:
  1. Send a telemetry message that triggers a low-battery alert
     (e.g. battery voltage < 20.0 via the gateway TCP port or 
      POST /api/v1/test/telemetry if test endpoint exists)
  2. Query the database:
     SELECT * FROM event_outbox 
     WHERE aggregate_id = '<machine-a1-uuid>' 
     ORDER BY created_at DESC LIMIT 5;

Expected:
  - A new event_outbox row exists with event_type = 'ALERT_TRANSITION'
  - published_at is NULL initially (not yet published)
  - payload contains alertId, alertType, severity, incidentState
  - organization_id matches Org A

Pass Criteria: Row exists with correct org, type, and NULL published_at.
```

### TC-1.2: Broker Confirm Marks Row as Published

```
ID:          TC-1.2
Phase:       1
Priority:    Critical
Objective:   Verify publisher confirms work — outbox row is marked published 
             only after RabbitMQ acknowledges
Precondition: RabbitMQ is running and healthy

Steps:
  1. Trigger an alert (as in TC-1.1)
  2. Wait 10 seconds (OutboxPublisher runs every 5s)
  3. Query:
     SELECT id, published_at, publish_attempts, claim_lease_until 
     FROM event_outbox 
     WHERE aggregate_id = '<machine-a1-uuid>' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - published_at is NOT NULL (set to a timestamp)
  - publish_attempts = 0 (no retries needed)
  - claim_lease_until is NULL (lease released after publish)

Pass Criteria: Row marked published within ~10s of creation.
```

### TC-1.3: Broker Failure Schedules Retry

```
ID:          TC-1.3
Phase:       1
Priority:    High
Objective:   Verify that when the broker is unavailable, the outbox row 
             is retried with exponential backoff
Precondition: Outbox has unpublished rows

Steps:
  1. Stop RabbitMQ: docker stop <rabbitmq-container>
  2. Trigger an alert
  3. Wait 15 seconds
  4. Query:
     SELECT publish_attempts, next_attempt_at, claim_lease_until 
     FROM event_outbox 
     WHERE published_at IS NULL 
     ORDER BY created_at DESC LIMIT 1;
  5. Start RabbitMQ: docker start <rabbitmq-container>
  6. Wait 60 seconds
  7. Re-query the same row

Expected:
  - After step 4: publish_attempts = 1, next_attempt_at is ~30s in future
  - After step 7: published_at is set, publish_attempts may be 1-2

Pass Criteria: Retry scheduled, then published after broker recovery.
```

### TC-1.4: Source Event Idempotency — Duplicate Delivery Ignored

```
ID:          TC-1.4
Phase:       1
Priority:    Critical
Objective:   Verify that duplicate RabbitMQ deliveries do not create 
             duplicate alerts
Precondition: RabbitMQ management UI accessible

Steps:
  1. Trigger a single alert event
  2. Wait for the outbox row to be published
  3. In RabbitMQ management UI, find the notification queue
  4. Manually requeue the message (or use the replay endpoint)
  5. Query:
     SELECT count(*) FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'LOW_BATTERY' 
     AND incident_state = 'OPEN';

Expected:
  - Only ONE alert row exists (no duplicate)
  - processed_events table has one entry for the source event ID

Pass Criteria: Re-delivered message is skipped, no duplicate alert.
```

### TC-1.5: Alert Acknowledgement Permission

```
ID:          TC-1.5
Phase:       1
Priority:    Critical
Objective:   Verify that only users with alert:acknowledge permission 
             can acknowledge alerts
Precondition: An open alert exists for Machine-A1

Steps:
  1. Login as Org A customer (has alert:acknowledge permission)
  2. POST /api/v1/alerts/{alertId}/acknowledge
     Expected: 200 OK, alert.acknowledged = true
  3. Create a new alert (trigger another low-battery event)
  4. Login as a user WITHOUT alert:acknowledge permission 
     (e.g. a viewer-only role if exists, or use Org B user)
  5. POST /api/v1/alerts/{alertId}/acknowledge
     Expected: 403 Forbidden

Pass Criteria: Authorized user can acknowledge; unauthorized user gets 403.
```

### TC-1.6: Machine-Assignment Access on Alert Read

```
ID:          TC-1.6
Phase:       1
Priority:    Critical
Objective:   Verify that non-admin users can only read/acknowledge alerts 
             for machines assigned to them
Precondition: Open alert exists on Machine-A1 (assigned to Org A customer) 
             and Machine-A2 (unassigned)

Steps:
  1. Login as Org A customer
  2. GET /api/v1/alerts/{alertId-for-Machine-A1}
     Expected: 200 OK with alert details
  3. GET /api/v1/alerts/{alertId-for-Machine-A2}
     Expected: 403 Forbidden (machine not assigned to this customer)
  4. Login as Org A admin
  5. GET /api/v1/alerts/{alertId-for-Machine-A2}
     Expected: 200 OK (admin bypasses assignment check)

Pass Criteria: Customer blocked from unassigned machine alerts; admin allowed.
```

---

## 4. Phase 2 — Rule Evaluation, Offline & Expiry Detection

### TC-2.1: Alert Rule — Low Battery Threshold

```
ID:          TC-2.1
Phase:       2
Priority:    Critical
Objective:   Verify that a battery-reading below threshold triggers an alert
Precondition: An alert rule exists for Machine-A1:
             alertType=LOW_BATTERY, operator=LT, threshold=20.0, 
             sustainMinutes=0, severity=WARNING

Steps:
  1. Send telemetry with battery = 25.0 (above threshold)
     Expected: No alert created
  2. Send telemetry with battery = 15.0 (below threshold)
  3. Wait 5 seconds
  4. Query:
     SELECT * FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'LOW_BATTERY' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - Alert exists with severity = 'WARNING', incident_state = 'OPEN'
  - observed_value = 15.0, observed_unit = 'battery'
  - occurrence_count = 1

Pass Criteria: Alert fires only when threshold is crossed.
```

### TC-2.2: Alert Rule — Sustain Window

```
ID:          TC-2.2
Phase:       2
Priority:    High
Objective:   Verify that sustainMinutes delays alert firing until the 
             condition persists for the full window
Precondition: Alert rule with sustainMinutes=2

Steps:
  1. Send telemetry with battery = 15.0 (below threshold)
  2. Wait 1 minute (less than sustain window)
  3. Query alerts — Expected: No alert yet (condition not sustained)
  4. Wait another 2 minutes (total 3 min, exceeds sustain=2)
  5. Query alerts — Expected: Alert exists

Pass Criteria: Alert fires only after sustained condition.
```

### TC-2.3: Alert Rule — Escalation

```
ID:          TC-2.3
Phase:       2
Priority:    High
Objective:   Verify that a sustained alert escalates to higher severity 
             and emits ESCALATED incident state
Precondition: Alert rule with escalationSeverity=CRITICAL, 
             escalationMinutes=5

Steps:
  1. Trigger a low-battery alert (battery < threshold)
  2. Wait for the alert to open (verify incident_state = 'OPEN')
  3. Wait until escalation time passes (escalationMinutes + buffer)
  4. Keep sending below-threshold telemetry to sustain the condition
  5. Query:
     SELECT severity, incident_state, occurrence_count 
     FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'LOW_BATTERY' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - severity = 'CRITICAL' (escalated from WARNING)
  - incident_state = 'ESCALATED'
  - occurrence_count >= 2

Pass Criteria: Severity escalated, ESCALATED state emitted.
```

### TC-2.4: Alert Rule — Auto-Recovery

```
ID:          TC-2.4
Phase:       2
Priority:    High
Objective:   Verify that when the condition clears, the alert auto-resolves
Precondition: Open alert exists for low battery

Steps:
  1. Send telemetry with battery = 80.0 (above threshold)
  2. Wait for recoveryMinutes to pass (default 5 min)
  3. Query:
     SELECT incident_state, resolved_at 
     FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'LOW_BATTERY' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - incident_state = 'RESOLVED'
  - resolved_at is NOT NULL

Pass Criteria: Alert resolves when condition clears.
```

### TC-2.5: Offline Detection — Device Goes Offline

```
ID:          TC-2.5
Phase:       2
Priority:    Critical
Objective:   Verify that a device which stops sending heartbeats is 
             detected as offline after the grace period
Precondition: Machine-A1 is online, grace-minutes=15

Steps:
  1. Note the current time
  2. Stop sending heartbeats from Machine-A1's device
  3. Wait 16 minutes (exceeds 15-min grace)
  4. Query:
     SELECT * FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'DEVICE_OFFLINE' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - Alert exists with severity = 'WARNING', incident_state = 'OPEN'
  - Message mentions "no heartbeat" or "offline"

Pass Criteria: Offline alert fires after grace period.
```

### TC-2.6: Offline Detection — Device Reconnects Within Grace

```
ID:          TC-2.6
Phase:       2
Priority:    High
Objective:   Verify that a device which reconnects within the grace 
             period does NOT trigger an offline alert
Precondition: Machine-A1 is online, grace-minutes=15

Steps:
  1. Stop sending heartbeats
  2. Wait 10 minutes (within 15-min grace)
  3. Resume sending heartbeats
  4. Wait 10 more minutes
  5. Query for DEVICE_OFFLINE alerts for Machine-A1

Expected:
  - No DEVICE_OFFLINE alert created (device reconnected within grace)

Pass Criteria: No false offline alert.
```

### TC-2.7: Offline Detection — Reconnected Device Resolves Open Incident

```
ID:          TC-2.7
Phase:       2
Priority:    High
Objective:   Verify that when a device reconnects after being offline, 
             the open offline incident is resolved
Precondition: An open DEVICE_OFFLINE alert exists for Machine-A1

Steps:
  1. Resume sending heartbeats from Machine-A1
  2. Wait for the next offline detection cycle (~1 min)
  3. Query:
     SELECT incident_state, resolved_at 
     FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'DEVICE_OFFLINE' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - incident_state = 'RESOLVED'
  - resolved_at is NOT NULL

Pass Criteria: Offline alert resolves on reconnect.
```

### TC-2.8: Expiry Detection — SIM Expiry Warning

```
ID:          TC-2.8
Phase:       2
Priority:    High
Objective:   Verify that SIM expiry warnings fire at configured milestones 
             (7, 3, 1 days before expiry)
Precondition: Machine-A1 has a SIM with expiry date 7 days from now

Steps:
  1. Set Machine-A1's SIM expiry to 7 days from now (in DB or via API)
  2. Wait for the expiry detection scheduler to run (~5 min, or trigger manually)
  3. Query:
     SELECT * FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'SIM_EXPIRING' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - Alert exists with severity = 'WARNING' (or INFO per config)
  - Message mentions "7 days" or the milestone

Pass Criteria: Expiry warning fires at the correct milestone.
```

### TC-2.9: Expiry Detection — New Recharge Cancels Open Expiry Alert

```
ID:          TC-2.9
Phase:       2
Priority:    High
Objective:   Verify that recharging a SIM resolves any open expiry alert
Precondition: An open SIM_EXPIRING alert exists for Machine-A1

Steps:
  1. Update Machine-A1's SIM expiry to 90 days from now (simulating recharge)
  2. Wait for the next expiry detection cycle
  3. Query:
     SELECT incident_state, resolved_at 
     FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'SIM_EXPIRING' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - incident_state = 'RESOLVED'
  - resolved_at is NOT NULL

Pass Criteria: Recharge resolves the expiry alert.
```

### TC-2.10: Condition Config Validation — Invalid Operator

```
ID:          TC-2.10
Phase:       2
Priority:    Medium
Objective:   Verify that creating a rule with an invalid operator is rejected
Precondition: Admin user logged in

Steps:
  1. POST /api/v1/alert-rules with conditionConfig:
     {"metric":"battery","operator":"INVALID","threshold":20.0}
  2. Expected: 400 Bad Request
  3. Verify error message mentions "Invalid operator" and lists valid ones

Pass Criteria: Invalid operator rejected with clear error.
```

### TC-2.11: Condition Config Validation — Missing Threshold

```
ID:          TC-2.11
Phase:       2
Priority:    Medium
Objective:   Verify that a rule without threshold is rejected
Precondition: Admin user logged in

Steps:
  1. POST /api/v1/alert-rules with conditionConfig:
     {"metric":"battery","operator":"LT"}
  2. Expected: 400 Bad Request
  3. Verify error message mentions "threshold"

Pass Criteria: Missing threshold rejected.
```

### TC-2.12: Condition Config Validation — windowMinutes Out of Range

```
ID:          TC-2.12
Phase:       2
Priority:    Medium
Objective:   Verify that windowMinutes > 1440 is rejected
Precondition: Admin user logged in

Steps:
  1. POST /api/v1/alert-rules with conditionConfig:
     {"metric":"battery","operator":"LT","threshold":20.0,"windowMinutes":2000}
  2. Expected: 400 Bad Request
  3. Verify error message mentions "1 and 1440"

Pass Criteria: Out-of-range windowMinutes rejected.
```

---

## 5. Phase 3 — Recipient Inbox, Preferences & APIs

### TC-3.1: Notification Inbox — Alert Creates Inbox Entry

```
ID:          TC-3.1
Phase:       3
Priority:    Critical
Objective:   Verify that an alert transition creates a notification_inbox 
             row for the assigned customer
Precondition: Machine-A1 is assigned to Org A customer

Steps:
  1. Trigger a low-battery alert on Machine-A1
  2. Wait for the notification event consumer to process (~5s)
  3. Query:
     SELECT * FROM notification_inbox 
     WHERE user_id = '<org-a-customer-uuid>' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - Inbox row exists with event_id matching the alert transition
  - organization_id = Org A
  - is_read = false
  - title and body are populated from the notification template

Pass Criteria: Inbox entry created for the assigned user.
```

### TC-3.2: Notification Inbox — Unassigned Machine Creates No Inbox Entry

```
ID:          TC-3.2
Phase:       3
Priority:    High
Objective:   Verify that alerts on unassigned machines do NOT create 
             inbox entries for customers
Precondition: Machine-A2 is NOT assigned to anyone

Steps:
  1. Trigger an alert on Machine-A2
  2. Wait 10 seconds
  3. Query:
     SELECT count(*) FROM notification_inbox 
     WHERE alert_id = '<machine-a2-alert-uuid>';

Expected:
  - Count = 0 (no inbox entry for unassigned machine)
  - Admin may still see the alert via GET /api/v1/alerts

Pass Criteria: No inbox entry for unassigned machine.
```

### TC-3.3: Notification Inbox — List with Pagination

```
ID:          TC-3.3
Phase:       3
Priority:    High
Objective:   Verify that the inbox list endpoint returns paginated results
Precondition: At least 25 inbox entries exist for the test user

Steps:
  1. Login as Org A customer
  2. GET /api/v1/notifications/inbox?page=0&size=10
  3. Verify response:
     - 10 items returned
     - totalElements >= 25
     - totalPages >= 3
  4. GET /api/v1/notifications/inbox?page=2&size=10
  5. Verify: 10 or fewer items (last page)

Pass Criteria: Pagination works correctly.
```

### TC-3.4: Notification Inbox — Page Size Capped at 100

```
ID:          TC-3.4
Phase:       3
Priority:    Medium
Objective:   Verify that requesting page size > 100 returns at most 100 items
Precondition: At least 150 inbox entries exist

Steps:
  1. GET /api/v1/notifications/inbox?page=0&size=500
  2. Verify response has at most 100 items (page size capped)

Pass Criteria: Page size capped at 100, not 500.
```

### TC-3.5: Notification Inbox — Combined Filters (Unread + Alert Type)

```
ID:          TC-3.5
Phase:       3
Priority:    High
Objective:   Verify that unread and alertType filters can be combined
Precondition: Mix of read/unread, LOW_BATTERY and DEVICE_OFFLINE inbox entries

Steps:
  1. GET /api/v1/notifications/inbox?unreadOnly=true&alertType=LOW_BATTERY
  2. Verify ALL returned items:
     - is_read = false
     - alert_type = 'LOW_BATTERY'
  3. GET /api/v1/notifications/inbox?unreadOnly=true&alertType=DEVICE_OFFLINE
  4. Verify ALL returned items:
     - is_read = false
     - alert_type = 'DEVICE_OFFLINE'

Pass Criteria: Both filters applied simultaneously.
```

### TC-3.6: Notification Inbox — Mark as Read

```
ID:          TC-3.6
Phase:       3
Priority:    Critical
Objective:   Verify that marking an inbox notification as read works
Precondition: An unread inbox entry exists

Steps:
  1. GET /api/v1/notifications/inbox — note an unread item's ID
  2. POST /api/v1/notifications/inbox/{id}/read
  3. GET /api/v1/notifications/inbox/{id}
  4. Verify: is_read = true, read_at is NOT NULL

Pass Criteria: Item marked as read with timestamp.
```

### TC-3.7: Notification Inbox — Acknowledge from Inbox

```
ID:          TC-3.7
Phase:       3
Priority:    High
Objective:   Verify that acknowledging a notification from the inbox 
             also acknowledges the underlying alert
Precondition: An unread inbox entry linked to an open alert exists

Steps:
  1. POST /api/v1/notifications/inbox/{id}/acknowledge
  2. Query the linked alert:
     SELECT acknowledged, acknowledged_at, acknowledged_by 
     FROM alerts WHERE id = '<alert-id>';
  3. Verify: acknowledged = true, acknowledged_at NOT NULL, 
            acknowledged_by = current user ID

Pass Criteria: Inbox acknowledgement propagates to the alert.
```

### TC-3.8: Notification Preferences — Get Default Preferences

```
ID:          TC-3.8
Phase:       3
Priority:    High
Objective:   Verify that a new user gets default notification preferences
Precondition: User has never set preferences

Steps:
  1. Login as a new user
  2. GET /api/v1/notifications/preferences
  3. Verify response includes default preferences for all event types:
     - LOW_BATTERY, VOLTAGE_DROP, GSM_SIGNAL_LOW, DEVICE_OFFLINE, SIM_EXPIRY
  4. Each preference has: channel (INBOX/PUSH/SMS), enabled, pushEnabled

Pass Criteria: Default preferences returned for all event types.
```

### TC-3.9: Notification Preferences — Update Preference

```
ID:          TC-3.9
Phase:       3
Priority:    High
Objective:   Verify that updating a preference persists and takes effect
Precondition: User has default preferences

Steps:
  1. PUT /api/v1/notifications/preferences with body:
     { "alertType": "LOW_BATTERY", "channel": "PUSH", 
       "enabled": true, "pushEnabled": true }
  2. GET /api/v1/notifications/preferences
  3. Verify the LOW_BATTERY preference reflects the update
  4. Trigger a LOW_BATTERY alert
  5. Verify: inbox entry created AND push delivery job enqueued 
     (if push is enabled)

Pass Criteria: Preference update persists and affects delivery.
```

### TC-3.10: Notification Preferences — Disable Channel Suppresses Delivery

```
ID:          TC-3.10
Phase:       3
Priority:    High
Objective:   Verify that disabling a channel prevents delivery via that channel
Precondition: User has LOW_BATTERY preference with enabled=false

Steps:
  1. Set LOW_BATTERY preference: enabled = false
  2. Trigger a LOW_BATTERY alert
  3. Wait 10 seconds
  4. Query:
     SELECT count(*) FROM notification_inbox 
     WHERE user_id = '<user-uuid>' 
     AND alert_type = 'LOW_BATTERY';

Expected:
  - Count = 0 (no inbox entry because preference is disabled)

Pass Criteria: Disabled preference suppresses delivery.
```

### TC-3.11: Event Catalog Endpoint

```
ID:          TC-3.11
Phase:       3
Priority:    Medium
Objective:   Verify that the event catalog endpoint returns supported event types
Precondition: User logged in

Steps:
  1. GET /api/v1/notifications/preferences/catalog
  2. Verify response is an array of event types, each with:
     - eventType (e.g. "LOW_BATTERY")
     - displayName (human-readable)
     - description
     - channels (array of supported channels)

Pass Criteria: Catalog returns all 5 supported event types.
```

### TC-3.12: Notification Inbox — Tenant Isolation

```
ID:          TC-3.12
Phase:       3
Priority:    Critical
Objective:   Verify that a user in Org A cannot see Org B's notifications
Precondition: Inbox entries exist in both Org A and Org B

Steps:
  1. Login as Org A customer
  2. GET /api/v1/notifications/inbox
  3. Verify ALL items have organization_id = Org A
  4. Login as Org B customer
  5. GET /api/v1/notifications/inbox
  6. Verify ALL items have organization_id = Org B

Pass Criteria: No cross-tenant data leakage.
```

### TC-3.13: Notification Inbox — Unique Key Includes Organization

```
ID:          TC-3.13
Phase:       3
Priority:    Medium
Objective:   Verify that the inbox unique constraint includes organization_id, 
             allowing the same event_id in different orgs
Precondition: Two orgs exist

Steps:
  1. Manually insert (or trigger) the same event_id in two different orgs:
     INSERT INTO notification_inbox 
       (id, organization_id, user_id, event_id, ...) 
     VALUES (..., '<org-a>', '<user-a>', '<same-event-id>', ...);
     INSERT INTO notification_inbox 
       (id, organization_id, user_id, event_id, ...) 
     VALUES (..., '<org-b>', '<user-b>', '<same-event-id>', ...);
  2. Verify: Both inserts succeed (no unique constraint violation)

Pass Criteria: Same event_id allowed in different organizations.
```

---

## 6. Phase 4 — Mobile Inbox & Alert Experience

### TC-4.1: Alerts Tab Shows Active Alerts

```
ID:          TC-4.1
Phase:       4
Priority:    Critical
Objective:   Verify that the Alerts tab displays active alerts for the user
Precondition: Mobile app running, user logged in, open alerts exist

Steps:
  1. Open the mobile app
  2. Navigate to the Alerts tab (bottom navigation)
  3. Verify: "Active Alerts" tab is selected by default
  4. Verify: Alert cards are displayed showing:
     - Alert type (e.g. "Low Battery")
     - Severity badge (WARNING/CRITICAL color)
     - Machine name
     - Time ago (e.g. "5 min ago")
  5. Tap an alert card
  6. Verify: Navigates to alert detail page (/app/alerts/{id})

Pass Criteria: Alerts tab shows active alerts, tap navigates to detail.
```

### TC-4.2: Alerts Tab — Inbox Sub-tab

```
ID:          TC-4.2
Phase:       4
Priority:    Critical
Objective:   Verify that the Inbox sub-tab shows notification history
Precondition: Inbox entries exist for the user

Steps:
  1. Open the Alerts tab
  2. Tap the "Inbox" sub-tab (second tab)
  3. Verify: Notification list is displayed
  4. Each item shows:
     - Title, body preview, time
     - Unread indicator (dot/badge) for unread items
  5. Verify: "Mark all read" action in AppBar works
  6. Verify: Filter icon opens filter bottom sheet

Pass Criteria: Inbox sub-tab shows notifications with read/unread state.
```

### TC-4.3: Alert Detail Page

```
ID:          TC-4.3
Phase:       4
Priority:    Critical
Objective:   Verify that the alert detail page shows full alert information
Precondition: An open alert exists

Steps:
  1. From the Alerts tab, tap an alert card
  2. Verify the detail page shows:
     - Alert type (title)
     - Severity badge
     - Full message
     - Status (OPEN/ESCALATED/RESOLVED)
     - Triggered time
     - Machine name
  3. Tap "View Machine" button
  4. Verify: Navigates to /app/machines/{machineId}
  5. Press back button
  6. Verify: Returns to the alerts list

Pass Criteria: Detail page shows all alert info, navigation works.
```

### TC-4.4: Notification Detail — Machine Unavailable

```
ID:          TC-4.4
Phase:       4
Priority:    High
Objective:   Verify that when a machine is unassigned/deleted, the 
             notification detail shows a friendly "Machine no longer 
             available" message instead of a raw error
Precondition: A notification exists for a machine that has been unassigned 
             from the current user

Steps:
  1. Unassign Machine-A1 from the Org A customer (via admin API)
  2. Open the mobile app as Org A customer
  3. Navigate to Alerts → Inbox
  4. Tap a notification for Machine-A1
  5. Verify: Detail page shows "Machine no longer available" message
  6. Verify: No raw exception or stack trace shown

Pass Criteria: Friendly error message, no raw exception.
```

### TC-4.5: Notification Badge on Alerts Tab

```
ID:          TC-4.5
Phase:       4
Priority:    High
Objective:   Verify that the unread notification count badge appears on 
             the Alerts tab icon
Precondition: Unread notifications exist

Steps:
  1. Ensure there are unread inbox notifications
  2. Look at the bottom navigation bar
  3. Verify: Alerts tab icon has a badge with the unread count
  4. Open the Inbox sub-tab
  5. Tap "Mark all read"
  6. Verify: Badge disappears or updates to 0

Pass Criteria: Badge reflects unread count, updates on mark-all-read.
```

### TC-4.6: WebSocket — Real-Time Notification Push

```
ID:          TC-4.6
Phase:       4
Priority:    Critical
Objective:   Verify that new notifications appear in real-time without 
             manual refresh
Precondition: Mobile app is open and in foreground, user logged in

Steps:
  1. Open the mobile app, go to Alerts → Inbox
  2. Trigger an alert on Machine-A1 (from another client/API)
  3. Within ~5 seconds, verify:
     - New notification appears in the inbox list automatically
     - Badge count increments
     - No manual pull-to-refresh needed

Pass Criteria: Notification appears in real-time via WebSocket.
```

### TC-4.7: WebSocket — Reconnect on App Resume

```
ID:          TC-4.7
Phase:       4
Priority:    High
Objective:   Verify that the WebSocket reconnects and inbox refreshes 
             when the app returns from background
Precondition: Mobile app is running

Steps:
  1. Open the app, go to Inbox
  2. Press the Home button to background the app
  3. Trigger 2-3 new alerts while the app is backgrounded
  4. Bring the app back to foreground
  5. Within ~3 seconds, verify:
     - Inbox list refreshes and shows the new notifications
     - Badge count updates
     - WebSocket reconnects (check logcat for socket reconnection logs)

Pass Criteria: App resume triggers refresh and reconnect.
```

### TC-4.8: WebSocket — Token Authentication

```
ID:          TC-4.8
Phase:       4
Priority:    Critical
Objective:   Verify that WebSocket connection requires a valid JWT token
Precondition: Mobile app or STOMP client available

Steps:
  1. Attempt to connect to ws://localhost:8080/ws without a token
  2. Verify: Connection rejected (401 or handshake fails)
  3. Connect with a valid token (via ?token=<jwt> query param)
  4. Verify: Connection succeeds
  5. Subscribe to /user/queue/notifications
  6. Verify: Subscription accepted

Pass Criteria: Unauthenticated WebSocket connections rejected.
```

### TC-4.9: WebSocket — STOMP Subscription Authorization

```
ID:          TC-4.9
Phase:       4
Priority:    Critical
Objective:   Verify that a user cannot subscribe to another user's 
             notification queue or another org's machine topic
Precondition: Two users in different orgs, STOMP client

Steps:
  1. Connect as Org A user with valid token
  2. Subscribe to /user/queue/notifications
     Expected: Accepted (own queue)
  3. Subscribe to /topic/machines/<machine-B1-uuid>
     Expected: Rejected (machine belongs to Org B)
  4. Subscribe to /topic/machines/<machine-A1-uuid>
     Expected: Accepted (machine belongs to Org A, user has access)

Pass Criteria: Cross-tenant subscriptions rejected.
```

### TC-4.10: Notifications Tab Merged into Alerts

```
ID:          TC-4.10
Phase:       4
Priority:    Medium
Objective:   Verify that there is no separate "Notifications" tab — 
             it is merged into the Alerts tab
Precondition: Mobile app running

Steps:
  1. Look at the bottom navigation bar
  2. Verify: Only 3 tabs exist (Machines, Alerts, Profile)
  3. Verify: No "Notifications" tab
  4. Tap the Alerts tab
  5. Verify: Two sub-tabs (Active Alerts, Inbox) are available

Pass Criteria: Notifications merged into Alerts tab, 3 main tabs only.
```

---

## 7. Phase 5 — FCM Push Delivery

### TC-5.1: Push Delivery — Token Registration

```
ID:          TC-5.1
Phase:       5
Priority:    Critical
Objective:   Verify that the mobile app registers its FCM token on login
Precondition: Mobile app installed, Firebase configured

Steps:
  1. Log into the mobile app
  2. Check logcat for "Registered device token" log
  3. Query DB:
     SELECT id, platform, is_active, token_fingerprint, 
            token_encrypted IS NOT NULL as is_encrypted
     FROM user_device_tokens 
     WHERE user_id = '<current-user-uuid>';

Expected:
  - One active token row exists
  - platform = 'android' (or 'ios')
  - token_fingerprint is NOT NULL
  - is_encrypted = true (token encrypted at rest)

Pass Criteria: Token registered, encrypted, fingerprinted.
```

### TC-5.2: Push Delivery — FCM Push Received on Device

```
ID:          TC-5.2
Phase:       5
Priority:    Critical
Objective:   Verify that an alert triggers an FCM push notification 
             on the user's device
Precondition: 
  - notification.push.enabled = true
  - User preference for the alert type has pushEnabled = true
  - Device token registered
  - App is in background or killed

Steps:
  1. Background the mobile app (or kill it)
  2. Trigger an alert on the user's assigned machine
  3. Wait ~15-30 seconds (push scheduler interval)
  4. Verify: A push notification appears in the device's notification tray
  5. Verify: Notification title and body match the alert template
  6. Verify: Tapping the notification opens the app and navigates to 
            the notification detail

Pass Criteria: Push received, displays correct content, tap navigates correctly.
```

### TC-5.3: Push Delivery — Foreground Notification Tap

```
ID:          TC-5.3
Phase:       5
Priority:    High
Objective:   Verify that when a push arrives while the app is in foreground, 
             tapping the local notification navigates to the detail
Precondition: App is in foreground

Steps:
  1. Keep the app in foreground
  2. Trigger an alert
  3. Verify: A local notification appears (foreground notification handler)
  4. Tap the notification
  5. Verify: App navigates to /app/notifications/{inboxId}

Pass Criteria: Foreground tap navigates to notification detail.
```

### TC-5.4: Push Delivery — Status ACCEPTED_BY_PROVIDER

```
ID:          TC-5.4
Phase:       5
Priority:    High
Objective:   Verify that a successfully sent push is marked 
             ACCEPTED_BY_PROVIDER (not SENT)
Precondition: Push delivery enabled, token registered

Steps:
  1. Trigger an alert
  2. Wait for push delivery (~30s)
  3. Query:
     SELECT status, provider_message_id, sent_at 
     FROM push_delivery_jobs 
     WHERE user_id = '<user-uuid>' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - status = 'ACCEPTED_BY_PROVIDER'
  - provider_message_id = FCM message ID (e.g. "projects/.../messages/123")
  - sent_at is NOT NULL

Pass Criteria: Status is ACCEPTED_BY_PROVIDER, not SENT.
```

### TC-5.5: Push Delivery — Invalid Token Handling

```
ID:          TC-5.5
Phase:       5
Priority:    High
Objective:   Verify that when FCM returns UNREGISTERED, the token is 
             invalidated and no further pushes are sent to it
Precondition: A registered token that FCM will reject as unregistered

Steps:
  1. Manually corrupt the FCM token in the DB (or use an expired token)
  2. Trigger an alert
  3. Wait for push delivery attempt
  4. Query:
     SELECT status, is_active, invalid_reason 
     FROM push_delivery_jobs j 
     JOIN user_device_tokens t ON t.id = j.device_token_id 
     WHERE j.user_id = '<user-uuid>' 
     ORDER BY j.created_at DESC LIMIT 1;

Expected:
  - Push job status = 'FAILED' or 'INVALID_TOKEN'
  - Token is_active = false
  - invalid_reason mentions 'UNREGISTERED' or 'invalid token'

Pass Criteria: Invalid token detected and deactivated.
```

### TC-5.6: Push Delivery — Disabled by Feature Switch

```
ID:          TC-5.6
Phase:       5
Priority:    High
Objective:   Verify that when notification.push.enabled=false, no push 
             jobs are enqueued but inbox entries are still created
Precondition: notification.push.enabled = false

Steps:
  1. Set notification.push.enabled = false (env var or application.yml)
  2. Restart the backend
  3. Trigger an alert
  4. Wait 10 seconds
  5. Query:
     SELECT count(*) FROM notification_inbox 
     WHERE user_id = '<user-uuid>' 
     AND alert_type = '<triggered-type>';
     -- Expected: 1 (inbox entry created)
  6. Query:
     SELECT count(*) FROM push_delivery_jobs 
     WHERE user_id = '<user-uuid>' 
     AND created_at > NOW() - INTERVAL '5 minutes';
     -- Expected: 0 (no push job enqueued)

Pass Criteria: Inbox works, push suppressed when disabled.
```

### TC-5.7: Push Delivery — Preference Disabled Suppresses Push

```
ID:          TC-5.7
Phase:       5
Priority:    High
Objective:   Verify that when pushEnabled=false in user preferences, 
             no push is sent but inbox entry is created
Precondition: User preference for LOW_BATTERY has pushEnabled=false, 
             enabled=true

Steps:
  1. Set LOW_BATTERY preference: enabled=true, pushEnabled=false
  2. Trigger a LOW_BATTERY alert
  3. Wait 10 seconds
  4. Verify: Inbox entry created (count = 1)
  5. Verify: No push_delivery_job created for this alert

Pass Criteria: Inbox delivery works, push suppressed by preference.
```

### TC-5.8: Push Delivery — Token Encryption at Rest

```
ID:          TC-5.8
Phase:       5
Priority:    Critical
Objective:   Verify that FCM tokens are encrypted in the database and 
             raw tokens are never stored
Precondition: A device token is registered

Steps:
  1. Query:
     SELECT token_encrypted, token_fingerprint, 
            LENGTH(token_encrypted) as enc_length
     FROM user_device_tokens 
     WHERE is_active = true;
  2. Verify: token_encrypted is NOT NULL and contains encrypted data 
            (not the raw FCM token format "dGhpcyBpcyBh...")
  3. Verify: token_fingerprint is a hash (not the raw token)
  4. Search for raw token in all tables:
     SELECT table_name FROM information_schema.columns 
     WHERE column_name LIKE '%token%' 
     AND table_schema = 'public';
  5. Verify: No column stores the raw FCM token

Pass Criteria: Tokens encrypted, fingerprints stored, no raw tokens in DB.
```

### TC-5.9: Android POST_NOTIFICATIONS Permission

```
ID:          TC-5.9
Phase:       5
Priority:    Medium
Objective:   Verify that the Android 13+ POST_NOTIFICATIONS permission 
             is declared and requested
Precondition: Android 13+ emulator or device

Steps:
  1. Install the app on an Android 13+ device
  2. Verify: App requests notification permission on first launch
  3. Grant the permission
  4. Trigger an alert
  5. Verify: Push notification appears in the notification tray
  6. Deny the permission (in Settings → Apps → YantraGO → Notifications)
  7. Trigger another alert
  8. Verify: No push notification appears (permission denied)

Pass Criteria: Permission declared, requested, and respected.
```

---

## 8. Phase 6 — Production Rollout & Operations

### TC-6.1: Metrics — Outbox Age Recorded

```
ID:          TC-6.1
Phase:       6
Priority:    Medium
Objective:   Verify that outbox row age is recorded as a metric on publish
Precondition: Metrics endpoint exposed (Prometheus format)

Steps:
  1. Trigger an alert and wait for outbox publication
  2. GET /actuator/prometheus (or scrape metrics)
  3. Search for: notification_outbox_age_seconds
  4. Verify: Metric exists with a non-zero count and reasonable max value

Pass Criteria: Outbox age metric recorded.
```

### TC-6.2: Metrics — Queue Depth Gauge

```
ID:          TC-6.2
Phase:       6
Priority:    Medium
Objective:   Verify that the notification queue depth is exposed as a gauge
Precondition: Messages in the notification queue

Steps:
  1. Trigger several alerts to populate the queue
  2. GET /actuator/prometheus
  3. Search for: notification_queue_depth
  4. Verify: Gauge value > 0 (reflects queued messages)
  5. Wait for messages to be consumed
  6. Re-scrape — Verify: Gauge value decreases

Pass Criteria: Queue depth gauge reflects actual queue state.
```

### TC-6.3: Metrics — Lease Recovery Counter

```
ID:          TC-6.3
Phase:       6
Priority:    Low
Objective:   Verify that lease recoveries (crashed worker row reclaims) 
             are counted
Precondition: Outbox has rows with expired leases

Steps:
  1. Trigger alerts to create outbox rows
  2. Kill the backend process mid-publication (simulate crash)
  3. Restart the backend
  4. Wait for the next publish cycle
  5. GET /actuator/prometheus
  6. Search for: notification_lease_recovery_total
  7. Verify: Counter > 0 (recovered rows from the crashed worker)

Pass Criteria: Lease recovery counter increments on crash recovery.
```

### TC-6.4: Metrics — Push Delivery Latency

```
ID:          TC-6.4
Phase:       6
Priority:    Medium
Objective:   Verify that push delivery latency is recorded
Precondition: Push delivery enabled, at least one push sent

Steps:
  1. Trigger an alert and wait for push delivery
  2. GET /actuator/prometheus
  3. Search for: notification_push_delivery_latency
  4. Verify: Timer metric exists with count, sum, and bucket values

Pass Criteria: Push latency metric recorded.
```

### TC-6.5: DLQ Replay — Idempotent

```
ID:          TC-6.5
Phase:       6
Priority:    High
Objective:   Verify that replaying the same DLQ message twice does not 
             create duplicate notifications
Precondition: A message exists in the DLQ

Steps:
  1. Identify a message in the notification DLQ
  2. POST /api/v1/admin/notifications/dlq/replay with the message ID
  3. Verify: Replay succeeds, notification created
  4. POST /api/v1/admin/notifications/dlq/replay with the SAME message ID
  5. Verify: Response indicates "ALREADY_REPLAYED" (idempotent)
  6. Verify: No duplicate notification created

Pass Criteria: Second replay is a no-op, no duplicates.
```

### TC-6.6: Bounded Backlog Protection

```
ID:          TC-6.6
Phase:       6
Priority:    Low
Objective:   Verify that when a user has > 1000 pending push jobs, 
             new pushes are skipped
Precondition: push.backlog.max-per-user = 1000

Steps:
  1. Insert 1001 PENDING push_delivery_jobs for a user:
     INSERT INTO push_delivery_jobs (id, user_id, status, ...) 
     SELECT gen_random_uuid(), '<user-uuid>', 'PENDING', ... 
     FROM generate_series(1, 1001);
  2. Trigger a new alert
  3. Wait for push enqueue
  4. Check backend logs for "Push backlog exceeded"
  5. Verify: No new push_delivery_job created for this alert

Pass Criteria: Backlog protection skips enqueue when threshold exceeded.
```

### TC-6.7: Audit Logging — Token Registration

```
ID:          TC-6.7
Phase:       6
Priority:    Medium
Objective:   Verify that token registration/unregistration is audit-logged
Precondition: Logging at INFO level

Steps:
  1. Log into the mobile app (triggers token registration)
  2. Check backend logs for:
     "AUDIT: token_registered user=<uuid> tokenId=<uuid> action=new"
  3. Log out (or unregister token)
  4. Check logs for:
     "AUDIT: token_unregistered user=<uuid> tokenId=<uuid>"

Pass Criteria: AUDIT log lines present for token lifecycle events.
```

### TC-6.8: Audit Logging — Preference Changes

```
ID:          TC-6.8
Phase:       6
Priority:    Medium
Objective:   Verify that preference changes are audit-logged
Precondition: User logged in

Steps:
  1. PUT /api/v1/notifications/preferences (change a preference)
  2. Check backend logs for:
     "AUDIT: preference_changed user=<uuid> channel=<channel> 
      alertType=<type> enabled=<bool> pushEnabled=<bool> action=..."

Pass Criteria: AUDIT log line present for preference change.
```

### TC-6.9: Audit Logging — Notification Acknowledgement

```
ID:          TC-6.9
Phase:       6
Priority:    Medium
Objective:   Verify that notification acknowledgement is audit-logged
Precondition: An unread inbox notification exists

Steps:
  1. POST /api/v1/notifications/inbox/{id}/acknowledge
  2. Check backend logs for:
     "AUDIT: notification_acknowledged user=<uuid> inboxId=<uuid>"

Pass Criteria: AUDIT log line present for acknowledgement.
```

### TC-6.10: Health Indicator — Queue Health

```
ID:          TC-6.10
Phase:       6
Priority:    Medium
Objective:   Verify that the notification queue health indicator reports 
             status
Precondition: Backend running

Steps:
  1. GET /actuator/health
  2. Verify: "notificationQueue" health indicator is present
  3. Verify: Status is "UP" when queue is processing normally
  4. Stop RabbitMQ
  5. GET /actuator/health
  6. Verify: Status is "DOWN" or "OUT_OF_SERVICE"

Pass Criteria: Health indicator reflects queue connectivity.
```

---

## 9. Cross-Cutting Security Tests

### TC-9.1: Cross-Tenant Alert Access — Denied

```
ID:          TC-9.1
Phase:       Security
Priority:    Critical
Objective:   Verify that Org A user cannot access Org B's alerts
Precondition: Alerts exist in both orgs

Steps:
  1. Login as Org A customer
  2. GET /api/v1/alerts/{org-B-alert-id}
  3. Expected: 403 Forbidden or 404 Not Found

Pass Criteria: Cross-tenant alert access denied.
```

### TC-9.2: Cross-Tenant Inbox Access — Denied

```
ID:          TC-9.2
Phase:       Security
Priority:    Critical
Objective:   Verify that Org A user cannot read Org B's inbox
Precondition: Inbox entries exist in both orgs

Steps:
  1. Login as Org A customer
  2. GET /api/v1/notifications/inbox/{org-B-inbox-id}
  3. Expected: 403 Forbidden or 404 Not Found

Pass Criteria: Cross-tenant inbox access denied.
```

### TC-9.3: Cross-User Notification Access — Denied

```
ID:          TC-9.3
Phase:       Security
Priority:    Critical
Objective:   Verify that a user cannot read another user's notifications 
             within the same org
Precondition: Two users in Org A (customer and operator), both have 
             inbox entries

Steps:
  1. Login as Org A customer
  2. GET /api/v1/notifications/inbox/{operator-inbox-id}
  3. Expected: 403 Forbidden (belongs to another user)

Pass Criteria: Cross-user inbox access denied within same org.
```

### TC-9.4: Expired JWT — All Endpoints Rejected

```
ID:          TC-9.4
Phase:       Security
Priority:    Critical
Objective:   Verify that an expired JWT is rejected on all protected endpoints
Precondition: Obtain a JWT, then wait for it to expire (or shorten expiry)

Steps:
  1. Login and obtain a JWT
  2. Wait for the JWT to expire (or use a short-expiry test token)
  3. GET /api/v1/notifications/inbox with the expired token
  4. Expected: 401 Unauthorized
  5. Repeat for /api/v1/alerts, /api/v1/notifications/preferences
  6. All should return 401

Pass Criteria: Expired JWT rejected on all endpoints.
```

### TC-9.5: WebSocket — Cross-User Subscription Denied

```
ID:          TC-9.5
Phase:       Security
Priority:    Critical
Objective:   Verify that a user cannot subscribe to another user's 
             private notification queue
Precondition: STOMP client, two users in same org

Steps:
  1. Connect as Org A customer with valid token
  2. Attempt to SUBSCRIBE to /user/queue/notifications 
     (this is the customer's own queue — should succeed)
  3. Attempt to SEND to /app/notifications/broadcast 
     (if such a destination exists — should be validated)
  4. Verify: Only authorized destinations are accepted

Pass Criteria: STOMP SEND/SUBSCRIBE validated against RBAC.
```

### TC-9.6: Organization ID Not Trusted from Frontend

```
ID:          TC-9.6
Phase:       Security
Priority:    Critical
Objective:   Verify that supplying a different organization_id in the 
             request body does not grant cross-tenant access
Precondition: Org A user logged in

Steps:
  1. POST /api/v1/notifications/preferences with body containing:
     { "organizationId": "<org-B-uuid>", ... }
  2. Verify: The preference is saved under Org A (from JWT), NOT Org B
  3. Query:
     SELECT organization_id FROM notification_preferences 
     WHERE user_id = '<org-a-user-uuid>';
  4. Verify: organization_id = Org A (from JWT, not from body)

Pass Criteria: organization_id from body is ignored, JWT value used.
```

---

## 10. Edge Cases & Failure Scenarios

### TC-10.1: Concurrent Alert Triggers — No Duplicates

```
ID:          TC-10.1
Phase:       Edge
Priority:    High
Objective:   Verify that triggering the same alert type concurrently 
             does not create duplicate open incidents
Precondition: Machine-A1 online

Steps:
  1. Send 5 telemetry messages with battery < threshold in rapid succession 
     (within 1 second)
  2. Wait 5 seconds
  3. Query:
     SELECT count(*) FROM alerts 
     WHERE machine_id = '<machine-a1-uuid>' 
     AND alert_type = 'LOW_BATTERY' 
     AND incident_state = 'OPEN';

Expected:
  - Count = 1 (only one open incident, duplicates suppressed by 
    source-event idempotency)

Pass Criteria: No duplicate incidents from concurrent triggers.
```

### TC-10.2: Backend Restart — No Lost Notifications

```
ID:          TC-10.2
Phase:       Edge
Priority:    High
Objective:   Verify that restarting the backend does not lose pending 
             outbox events
Precondition: Outbox has unpublished rows

Steps:
  1. Trigger several alerts to populate the outbox
  2. Stop RabbitMQ (so outbox rows can't be published)
  3. Restart the backend
  4. Start RabbitMQ
  5. Wait 30 seconds
  6. Query:
     SELECT count(*) FROM event_outbox 
     WHERE published_at IS NULL 
     AND created_at < NOW() - INTERVAL '1 minute';

Expected:
  - Count = 0 (all rows published after restart)

Pass Criteria: No outbox events lost on backend restart.
```

### TC-10.3: Empty Inbox — Friendly Empty State

```
ID:          TC-10.3
Phase:       Edge
Priority:    Medium
Objective:   Verify that an empty inbox shows a friendly empty state, 
             not a blank screen
Precondition: New user with no notifications

Steps:
  1. Login as a new user (no notifications)
  2. Navigate to Alerts → Inbox
  3. Verify: Empty state message is shown (e.g. "No notifications yet")
  4. Verify: An icon or illustration is displayed

Pass Criteria: Friendly empty state, no blank screen.
```

### TC-10.4: Network Error — Error State with Retry

```
ID:          TC-10.4
Phase:       Edge
Priority:    Medium
Objective:   Verify that a network error shows an error state with retry, 
             not a crash or blank screen
Precondition: Mobile app running

Steps:
  1. Open the Inbox tab
  2. Turn off network (airplane mode or disable network in emulator)
  3. Pull to refresh
  4. Verify: Error state is shown with a "Retry" button
  5. Tap "Retry" (still offline) — verify error persists gracefully
  6. Turn network back on
  7. Tap "Retry" — verify inbox loads

Pass Criteria: Error handled gracefully with retry option.
```

### TC-10.5: Large Notification Volume — Pagination Performance

```
ID:          TC-10.5
Phase:       Edge
Priority:    Low
Objective:   Verify that the inbox list performs well with a large number 
             of notifications
Precondition: 500+ inbox entries for the test user

Steps:
  1. Insert 500 notification_inbox rows for the user (via SQL or by 
     triggering many alerts)
  2. GET /api/v1/notifications/inbox?page=0&size=20
  3. Measure response time
  4. Verify: Response time < 2 seconds
  5. GET /api/v1/notifications/inbox?page=24&size=20 (last page)
  6. Verify: Response time < 2 seconds

Pass Criteria: Pagination performs well at scale.
```

### TC-10.6: Multi-Instance Outbox — No Duplicate Publishing

```
ID:          TC-10.6
Phase:       Edge
Priority:    High
Objective:   Verify that two backend instances do not publish the same 
             outbox row
Precondition: Two backend instances running (or simulate with two 
             OutboxPublisher threads)

Steps:
  1. Start two backend instances (ports 8080 and 8081)
  2. Trigger an alert
  3. Wait for outbox publication
  4. Query:
     SELECT publish_attempts, published_at 
     FROM event_outbox 
     WHERE aggregate_id = '<machine-uuid>' 
     ORDER BY created_at DESC LIMIT 1;

Expected:
  - published_at is set once
  - publish_attempts = 0 (no contention)
  - Only one notification_inbox entry created (no duplicate)

Pass Criteria: SKIP LOCKED prevents duplicate publishing across instances.
```

### TC-10.7: Token Re-registration — Same Token Different User

```
ID:          TC-10.7
Phase:       Edge
Priority:    Medium
Objective:   Verify that when the same physical device registers a token 
             for a different user, the old token is deactivated
Precondition: Two users on the same device (e.g. shared phone)

Steps:
  1. Login as User A on the device — token registered
  2. Logout
  3. Login as User B on the same device — same FCM token
  4. Query:
     SELECT user_id, is_active 
     FROM user_device_tokens 
     WHERE token_fingerprint = '<fingerprint>';

Expected:
  - User A's token row: is_active = false (deactivated)
  - User B's token row: is_active = true (new active registration)

Pass Criteria: Old user's token deactivated, new user's token active.
```

---

## 11. Test Execution Summary Template

```
Test Run:        [Run ID / Date]
Environment:     [Local / Staging / Production]
Backend Version: [Git commit hash]
Mobile Version:  [Build number]
Tester:          [Name]

+----------+-------+---------+----------+--------+----------+
| Test ID  | Phase | Result  | Duration | Notes  | Evidence |
+----------+-------+---------+----------+--------+----------+
| TC-1.1   |   1   | PASS    |   15s    |        | Screenshot|
| TC-1.2   |   1   | PASS    |   12s    |        | DB query  |
| TC-1.3   |   1   | FAIL    |   45s    | Retry  | Log file  |
| ...      | ...   | ...     |   ...    | ...    | ...       |
+----------+-------+---------+----------+--------+----------+

Summary:
  Total:     [N]
  Passed:    [N]
  Failed:    [N]
  Blocked:   [N]
  Skipped:   [N]

Pass Rate:   [X]%

Critical Failures:
  - [List any Critical-priority failures that block release]

Sign-off:
  QA:        _________________  Date: ________
  Dev Lead:   _________________  Date: ________
  Product:    _________________  Date: ________
```

---

## Appendix A: Quick API Reference

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/alerts` | List alerts (tenant-scoped) |
| GET | `/api/v1/alerts/{id}` | Get alert detail |
| POST | `/api/v1/alerts/{id}/acknowledge` | Acknowledge alert |
| POST | `/api/v1/alert-rules` | Create alert rule |
| GET | `/api/v1/alert-rules` | List alert rules |
| GET | `/api/v1/notifications/inbox` | List inbox notifications |
| GET | `/api/v1/notifications/inbox/{id}` | Get inbox notification detail |
| POST | `/api/v1/notifications/inbox/{id}/read` | Mark as read |
| POST | `/api/v1/notifications/inbox/{id}/acknowledge` | Acknowledge from inbox |
| GET | `/api/v1/notifications/preferences` | Get user preferences |
| PUT | `/api/v1/notifications/preferences` | Update preferences |
| GET | `/api/v1/notifications/preferences/catalog` | Get event catalog |
| POST | `/api/v1/notifications/device-tokens` | Register device token |
| DELETE | `/api/v1/notifications/device-tokens/{id}` | Unregister device token |
| POST | `/api/v1/admin/notifications/dlq/replay` | Replay DLQ message |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Metrics (if Prometheus enabled) |
| WS | `/ws` | WebSocket STOMP endpoint |
| STOMP SUB | `/user/queue/notifications` | User's private notification queue |
| STOMP SUB | `/topic/machines/{id}` | Machine-scoped alerts (authorized) |

---

## Appendix B: Database Query Cheat Sheet

```sql
-- Recent outbox events
SELECT id, event_type, published_at, publish_attempts, created_at
FROM event_outbox
ORDER BY created_at DESC LIMIT 10;

-- Open alerts for a machine
SELECT id, alert_type, severity, incident_state, occurrence_count, 
       created_at, resolved_at
FROM alerts
WHERE machine_id = '<machine-uuid>'
  AND incident_state = 'OPEN'
ORDER BY created_at DESC;

-- User's inbox (unread)
SELECT id, event_id, title, body, is_read, alert_type, created_at
FROM notification_inbox
WHERE user_id = '<user-uuid>'
  AND is_read = false
ORDER BY created_at DESC;

-- User's device tokens (verify encryption)
SELECT id, platform, is_active, 
       token_fingerprint IS NOT NULL as has_fingerprint,
       token_encrypted IS NOT NULL as is_encrypted,
       last_used_at
FROM user_device_tokens
WHERE user_id = '<user-uuid>';

-- Push delivery jobs
SELECT id, status, provider_message_id, attempt_count, 
       sent_at, error_message
FROM push_delivery_jobs
WHERE user_id = '<user-uuid>'
ORDER BY created_at DESC LIMIT 10;

-- User preferences
SELECT alert_type, channel, enabled, push_enabled
FROM notification_preferences
WHERE user_id = '<user-uuid>';

-- Processed events (idempotency check)
SELECT count(*) FROM processed_events
WHERE source_event_id = '<event-uuid>';
```
