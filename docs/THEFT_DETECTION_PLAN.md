# Theft Detection — Implementation Plan

## Overview

Two server-side approaches for detecting machine theft/movement:

- **Phase 9 (Approach B):** Speed-based movement alert — triggers when GPS speed exceeds a threshold
- **Phase 10 (Approach C):** Geo-fencing — triggers when machine moves outside a defined radius

Both approaches are server-side and don't require device firmware changes.

---

## Phase 9: Speed-Based Movement Detection (Approach B)

### Concept

The BR05/Concox V5 device reports GPS speed (km/h) in every 0x22 location packet. This speed is already stored in `device_locations.speed` and `location_history.speed`. A stationary fencing machine should report speed = 0. If speed > threshold (e.g., 5 km/h), the machine is moving — likely theft.

### Why This Works

- Speed data is **already being captured and stored** — no protocol changes needed
- The `AlertGenerationService` already evaluates rules with thresholds and hysteresis
- Just needs a new metric type ("speed") and a new alert type ("MACHINE_MOVING")
- Uses the existing alert rule infrastructure (sustain window, recovery window, escalation)

### Limitations

- Depends on GPS fix frequency — if the device reports every 5 minutes, movement is detected with up to 5 min delay
- If the machine is carried slowly (walking speed ~3-5 km/h), a low threshold is needed
- If GPS is jammed or blocked (e.g., inside a van), no speed data arrives — falls back to DEVICE_OFFLINE alert

---

### Phase 9.1: Backend — Speed Metric + Alert Type + Templates

**Prompt:**

```
Implement Phase 9.1: Add speed-based movement alert to the backend.

1. AlertGenerationService.java (backend/src/main/java/com/yantrago/api/service/AlertGenerationService.java):
   - Add "speed" as a 4th supported metric in queryLatestTelemetryValue()
   - When metric = "speed", query from device_locations table:
     SELECT speed FROM device_locations WHERE organization_id = ? AND machine_id = ?
     ORDER BY recorded_at DESC LIMIT 1
   - (device_locations has the latest speed; location_history is partitioned and slower for single-row lookup)

2. AlertRuleService.java (backend/src/main/java/com/yantrago/api/service/AlertRuleService.java):
   - Add "MACHINE_MOVING" to SUPPORTED_RULE_TYPES set
   - This allows admins to create alert rules with alertType = MACHINE_MOVING

3. NotificationFeatureSwitches.java:
   - Add "MACHINE_MOVING" to the known notification families list
   - This ensures push delivery is enabled for this alert type by default

4. NotificationPreferenceService.java — getEventCatalog():
   - Add EventCatalogDto entry:
     type: "MACHINE_MOVING"
     label: "Machine Moving"
     description: "Triggered when the machine GPS speed exceeds the configured threshold — possible theft."
     channels: ["PUSH", "EMAIL", "SMS", "IN_APP"]

5. V42 migration (database/migrations/V42__machine_moving_templates.sql):
   Add notification templates for all 3 locales:
   
   English:
   - MACHINE_MOVING / OPEN: "Machine Moving" / "Machine {machineName} is moving (speed: {observedValue} km/h). Possible theft — verify immediately."
   - MACHINE_MOVING / RESOLVED: "Machine Stopped" / "Machine {machineName} has stopped moving."
   - MACHINE_MOVING / ESCALATED: "Machine Moving — Escalated" / "Machine {machineName} has been moving for an extended period (speed: {observedValue} km/h). Escalation triggered."
   
   Hindi:
   - MACHINE_MOVING / OPEN: "मशीन चल रही है" / "मशीन {machineName} चल रही है (गति: {observedValue} किमी/घं)। संभावित चोरी — तुरंत जांचें।"
   - MACHINE_MOVING / RESOLVED: "मशीन रुक गई" / "मशीन {machineName} चलना बंद कर दिया है।"
   - MACHINE_MOVING / ESCALATED: "मशीन चल रही है — उन्नत" / "मशीन {machineName} लंबे समय से चल रही है (गति: {observedValue} किमी/घं)। उन्नति ट्रिगर की गई।"
   
   Marathi:
   - MACHINE_MOVING / OPEN: "मशीन हलत आहे" / "मशीन {machineName} हलत आहे (वेग: {observedValue} किमी/ता)। शक्य चोरी — त्वरित तपासा."
   - MACHINE_MOVING / RESOLVED: "मशीन थांबले" / "मशीन {machineName} हलणे बंद झाले आहे."
   - MACHINE_MOVING / ESCALATED: "मशीन हलत आहे — वाढ" / "मशीन {machineName} दीर्घ काळ हलत आहे (वेग: {observedValue} किमी/ता). वाढवण्याची प्रक्रिया सुरू झाली."

6. Admin web alerts page (admin-web/src/app/(admin)/alerts/page.tsx):
   - Add "MACHINE_MOVING" to ALERT_TYPE_LABELS with label "Machine Moving"

7. Build backend, deploy, verify V42 migration applies, verify AlertGenerationService compiles.
8. Create a default MACHINE_MOVING alert rule via SQL or admin API:
   conditionConfig: {"metric": "speed", "operator": ">", "threshold": 5, "windowMinutes": 2}
   sustainMinutes: 1 (trigger after 1 min of movement)
   recoveryMinutes: 5 (resolve after 5 min of no movement)
   severity: CRITICAL
   escalationMinutes: 10
   escalationSeverity: CRITICAL
```

**Files to modify:**
- `backend/src/main/java/com/yantrago/api/service/AlertGenerationService.java`
- `backend/src/main/java/com/yantrago/api/service/AlertRuleService.java`
- `backend/src/main/java/com/yantrago/api/service/NotificationPreferenceService.java`
- `backend/src/main/java/com/yantrago/api/config/NotificationFeatureSwitches.java`
- `database/migrations/V42__machine_moving_templates.sql` (new)
- `admin-web/src/app/(admin)/alerts/page.tsx`

**Verification:**
- V42 migration applies successfully
- Admin can create a MACHINE_MOVING alert rule via POST /api/v1/alert-rules
- When a GPS packet with speed > 5 arrives, an alert is generated after the sustain window
- Notification inbox item is created with the correct locale template
- Push notification is delivered

---

### Phase 9.2: Mobile — Movement Alert UI

**Prompt:**

```
Implement Phase 9.2: Add MACHINE_MOVING alert type to mobile UI.

1. mobile/lib/models/notification_inbox.dart:
   - Add "MACHINE_MOVING" to the isCommand or alert type recognition
   - Add a getter: isMovementAlert => alertType == 'MACHINE_MOVING'
   - Add to alertTypeLabel: "MACHINE_MOVING" => "Machine Moving"

2. mobile/lib/features/notifications/widgets/notification_card.dart:
   - Add an icon for MACHINE_MOVING: Icons.directions_run or Icons.local_shipping
   - Add a tone: StatusTone.danger (critical — possible theft)

3. mobile/lib/features/notifications/widgets/notification_inbox_view.dart:
   - Add a filter chip: "Movement" → _alertType == 'MACHINE_MOVING'

4. mobile/lib/features/notifications/pages/notification_detail_page.dart:
   - Add a friendly status label for MACHINE_MOVING alerts
   - Show speed value prominently if available

5. Build APK, verify on emulator, commit and push.
```

**Files to modify:**
- `mobile/lib/models/notification_inbox.dart`
- `mobile/lib/features/notifications/widgets/notification_card.dart`
- `mobile/lib/features/notifications/widgets/notification_inbox_view.dart`
- `mobile/lib/features/notifications/pages/notification_detail_page.dart`

**Verification:**
- Flutter analyze passes
- APK builds successfully
- Movement notifications show with correct icon, label, and tone
- Filter chip works

---

## Phase 10: Geo-Fencing (Approach C)

### Concept

Admin defines a circular geo-fence around each machine's installed location (e.g., 100m radius). Every GPS location packet is checked against the geo-fence using PostGIS. If the machine moves outside the radius, a GEOFENCE_BREACH alert is generated. When it returns inside, the alert is resolved.

### Why This Works

- PostGIS is already installed and configured (PostgreSQL 15 + PostGIS)
- `location_history` table already has `location_geo GEOGRAPHY(POINT, 4326)` column with GIST indexes
- The GPS ingestion pipeline already stores every location update
- PostGIS `ST_DWithin()` can check distance in meters — very fast with GIST index

### Advantages Over Speed-Based Detection

- Works even if speed isn't reported (e.g., machine carried slowly, GPS drift)
- More precise — detects exact distance from installed location
- Can have different radii per machine (tighter for high-value machines)
- Can detect return-to-location (auto-resolve)

### Limitations

- Requires admin to set up a geo-fence per machine (or auto-create from first GPS fix)
- GPS drift may cause false positives (mitigate with 50-100m minimum radius)
- If GPS is jammed, no breach is detected (falls back to DEVICE_OFFLINE)

---

### Phase 10.1: Backend — Geofence Table + CRUD API

**Prompt:**

```
Implement Phase 10.1: Create geofence infrastructure.

1. V43 migration (database/migrations/V43__geofences.sql):
   CREATE TABLE geofences (
       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
       machine_id      UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
       name            VARCHAR(255) NOT NULL,
       center          GEOGRAPHY(POINT, 4326) NOT NULL,
       radius_meters   INTEGER NOT NULL DEFAULT 100,
       is_active       BOOLEAN NOT NULL DEFAULT true,
       created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
       updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
       UNIQUE (organization_id, machine_id)
   );
   
   CREATE INDEX idx_geofences_org_machine ON geofences(organization_id, machine_id) WHERE is_active = true;
   CREATE INDEX idx_geofences_center ON geofences USING GIST(center);

2. Geofence model (backend/src/main/java/com/yantrago/api/model/Geofence.java):
   - JPA entity matching the table above
   - Use @Column(name = "center", columnDefinition = "geography(Point,4326)")
   - Store center as a String (WKT: "POINT(lng lat)") or use PostGIS-specific handling

3. GeofenceDto (backend/src/main/java/com/yantrago/api/dto/geofence/GeofenceDto.java):
   - Fields: id, machineId, name, latitude, longitude, radiusMeters, isActive
   - Use latitude/longitude in DTO, convert to/from PostGIS POINT internally

4. GeofenceRequest (backend/src/main/java/com/yantrago/api/dto/geofence/GeofenceRequest.java):
   - Fields: machineId, name, latitude, longitude, radiusMeters
   - Validation: @NotNull machineId, latitude (-90 to 90), longitude (-180 to 180), radiusMeters (min 10, max 10000)

5. GeofenceRepository (backend/src/main/java/com/yantrago/api/repository/GeofenceRepository.java):
   - findByOrganizationIdAndMachineId
   - findActiveByOrganizationId
   - findActiveByMachineId

6. GeofenceService (backend/src/main/java/com/yantrago/api/service/GeofenceService.java):
   - createGeofence(orgId, request) — creates or updates (one active geofence per machine)
   - getGeofence(orgId, machineId)
   - listGeofences(orgId)
   - deleteGeofence(orgId, geofenceId)
   - Uses JdbcTemplate for PostGIS operations (ST_MakePoint, ST_SetSRID, ST_GeogFromText)
   - Tenant isolation: organization_id from JWT, never from request body
   - When creating: convert lat/lng to PostGIS: ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography

7. GeofenceController (backend/src/main/java/com/yantrago/api/controller/GeofenceController.java):
   - POST /api/v1/geofences — create/update geofence
   - GET /api/v1/geofences — list all geofences for org
   - GET /api/v1/geofences/{machineId} — get geofence for a machine
   - DELETE /api/v1/geofences/{geofenceId} — delete geofence
   - @PreAuthorize on all endpoints
   - Returns GeofenceDto, never entity

8. Build backend, deploy, verify V43 migration applies, test CRUD via curl.
```

**Files to create:**
- `database/migrations/V43__geofences.sql`
- `backend/src/main/java/com/yantrago/api/model/Geofence.java`
- `backend/src/main/java/com/yantrago/api/dto/geofence/GeofenceDto.java`
- `backend/src/main/java/com/yantrago/api/dto/geofence/GeofenceRequest.java`
- `backend/src/main/java/com/yantrago/api/repository/GeofenceRepository.java`
- `backend/src/main/java/com/yantrago/api/service/GeofenceService.java`
- `backend/src/main/java/com/yantrago/api/controller/GeofenceController.java`

**Verification:**
- V43 migration applies
- POST /api/v1/geofences creates a geofence
- GET /api/v1/geofences returns list
- DELETE removes geofence
- Tenant isolation enforced (can't access other org's geofences)

---

### Phase 10.2: Backend — Geo-Fence Breach Detection

**Prompt:**

```
Implement Phase 10.2: Detect geo-fence breaches when GPS locations are ingested.

1. GeofenceBreachService (backend/src/main/java/com/yantrago/api/service/GeofenceBreachService.java):
   - checkBreach(UUID orgId, UUID machineId, double latitude, double longitude):
     - Query: SELECT id, radius_meters, ST_Distance(center, ST_MakePoint(?, ?)::geography) as distance
              FROM geofences
              WHERE organization_id = ? AND machine_id = ? AND is_active = true
     - If no geofence exists for this machine → return (no check)
     - If distance > radius_meters → breach detected
     - If distance <= radius_meters → inside (resolve if was breached)
   - Uses CanonicalAlertService to generate/resolve alerts:
     - Breach: processAlertEvent(machineId, "GEOFENCE_BREACH", "CRITICAL", message, instant, distance, "meters", null)
     - Resolve: resolveIncident(orgId, machineId, "GEOFENCE_BREACH", message)

2. Hook into the location ingestion pipeline:
   - Find where GPS locations are saved to device_locations/location_history
   - After saving, call geofenceBreachService.checkBreach(orgId, machineId, lat, lng)
   - This should be in the telemetry/location consumer or the GpsIngestService
   - Look at: backend/src/main/java/com/yantrago/api/service/ — find where TelemetryMessage with location is processed
   - Or: backend/src/main/java/com/yantrago/api/queue/ — find the telemetry consumer

3. Add "GEOFENCE_BREACH" to:
   - NotificationFeatureSwitches known families
   - NotificationPreferenceService.getEventCatalog():
     type: "GEOFENCE_BREACH"
     label: "Geo-Fence Breach"
     description: "Triggered when the machine moves outside its defined geo-fence boundary — possible theft."
     channels: ["PUSH", "EMAIL", "SMS", "IN_APP"]

4. V44 migration (database/migrations/V44__geofence_breach_templates.sql):
   Add notification templates for all 3 locales:
   
   English:
   - GEOFENCE_BREACH / OPEN: "Geo-Fence Breach" / "Machine {machineName} has moved {observedValue}m outside its geo-fence. Possible theft — verify immediately."
   - GEOFENCE_BREACH / RESOLVED: "Machine Back in Zone" / "Machine {machineName} has returned within its geo-fence boundary."
   - GEOFENCE_BREACH / ESCALATED: "Geo-Fence Breach — Escalated" / "Machine {machineName} has been outside its geo-fence for an extended period ({observedValue}m). Escalation triggered."
   
   Hindi:
   - GEOFENCE_BREACH / OPEN: "जियो-फेंस उल्लंघन" / "मशीन {machineName} अपनी जियो-फेंस सीमा से {observedValue}मी बाहर चली गई है। संभावित चोरी — तुरंत जांचें।"
   - GEOFENCE_BREACH / RESOLVED: "मशीन वापस क्षेत्र में" / "मशीन {machineName} अपनी जियो-फेंस सीमा के भीतर वापस आ गई है।"
   - GEOFENCE_BREACH / ESCALATED: "जियो-फेंस उल्लंघन — उन्नत" / "मशीन {machineName} लंबे समय से अपनी जियो-फेंस के बाहर है ({observedValue}मी)। उन्नति ट्रिगर की गई।"
   
   Marathi:
   - GEOFENCE_BREACH / OPEN: "जिओ-फेन्स उल्लंघन" / "मशीन {machineName} त्याच्या जिओ-फेन्स सीमेबाहेर {observedValue}मी गेली आहे. शक्य चोरी — त्वरित तपासा."
   - GEOFENCE_BREACH / RESOLVED: "मशीन परत क्षेत्रात" / "मशीन {machineName} त्याच्या जिओ-फेन्स सीमेत परत आली आहे."
   - GEOFENCE_BREACH / ESCALATED: "जिओ-फेन्स उल्लंघन — वाढ" / "मशीन {machineName} दीर्घ काळ जिओ-फेन्स बाहेर आहे ({observedValue}मी). वाढवण्याची प्रक्रिया सुरू झाली."

5. Admin web alerts page: Add "GEOFENCE_BREACH" to ALERT_TYPE_LABELS with label "Geo-Fence Breach"

6. Build backend, deploy, verify V44 migration applies, test breach detection by:
   - Creating a geofence at a known location
   - Simulating a GPS packet outside the radius
   - Verifying alert is generated
```

**Files to create/modify:**
- `backend/src/main/java/com/yantrago/api/service/GeofenceBreachService.java` (new)
- `backend/src/main/java/com/yantrago/api/service/NotificationPreferenceService.java` (modify)
- `backend/src/main/java/com/yantrago/api/config/NotificationFeatureSwitches.java` (modify)
- `database/migrations/V44__geofence_breach_templates.sql` (new)
- `admin-web/src/app/(admin)/alerts/page.tsx` (modify)
- Location ingestion hook point (find and modify)

**Verification:**
- V44 migration applies
- Geofence breach detection triggers when GPS is outside radius
- Alert is generated with correct template
- Push notification is delivered
- Alert resolves when machine returns inside geo-fence

---

### Phase 10.3: Admin Web — Geofence Management UI

**Prompt:**

```
Implement Phase 10.3: Add geofence management page to admin web.

1. admin-web/src/app/(admin)/geofences/page.tsx:
   - Table listing all geofences: machine name, center coordinates, radius, status
   - Create/Edit form: select machine, enter latitude/longitude (or pick on map), radius slider (10-10000m)
   - Delete button with confirmation
   - "Auto-set from current location" button — fetches machine's last known GPS and pre-fills the form
   - Active/inactive toggle

2. admin-web/src/app/(admin)/layout.tsx:
   - Add navigation item: { href: '/geofences', label: 'Geo-Fences', icon: MapPin }

3. Use React Query for data fetching (same pattern as commands/alerts pages)
4. Use the card/table components from admin-web/src/components/ui/
5. Build admin web, deploy, verify /geofences returns HTTP 200
```

**Files to create/modify:**
- `admin-web/src/app/(admin)/geofences/page.tsx` (new)
- `admin-web/src/app/(admin)/layout.tsx` (modify)

**Verification:**
- /geofences page loads
- Can create a geofence
- Can edit/delete geofences
- Navigation link appears in sidebar

---

### Phase 10.4: Mobile — Geofence Status Display

**Prompt:**

```
Implement Phase 10.4: Show geofence status in mobile app.

1. mobile/lib/features/machines/providers/geofence_provider.dart:
   - FutureProvider.family<Geofence?, String> that fetches GET /api/v1/geofences/{machineId}
   - Returns null if no geofence is configured

2. mobile/lib/features/machines/widgets/geofence_status_card.dart:
   - Shows: geofence name, radius, current distance from center, in/out status
   - Green badge if inside, red badge if outside
   - "No geofence configured" if null

3. mobile/lib/features/machines/pages/machine_detail_page.dart:
   - Add GeofenceStatusCard below the LocationCard

4. Add GEOFENCE_BREACH to mobile notification UI:
   - notification_inbox.dart: alertTypeLabel "GEOFENCE_BREACH" => "Geo-Fence Breach"
   - notification_card.dart: icon Icons.gps_off or Icons.warning, tone StatusTone.danger
   - notification_inbox_view.dart: filter chip "Geo-Fence" → 'GEOFENCE_BREACH'
   - notification_detail_page.dart: friendly label for GEOFENCE_BREACH

5. Build APK, verify on emulator, commit and push.
```

**Files to create/modify:**
- `mobile/lib/features/machines/providers/geofence_provider.dart` (new)
- `mobile/lib/features/machines/widgets/geofence_status_card.dart` (new)
- `mobile/lib/features/machines/pages/machine_detail_page.dart` (modify)
- `mobile/lib/models/notification_inbox.dart` (modify)
- `mobile/lib/features/notifications/widgets/notification_card.dart` (modify)
- `mobile/lib/features/notifications/widgets/notification_inbox_view.dart` (modify)
- `mobile/lib/features/notifications/pages/notification_detail_page.dart` (modify)

**Verification:**
- Flutter analyze passes
- APK builds
- Geofence status card appears on machine detail page
- GEOFENCE_BREACH notifications display correctly

---

### Phase 10.5: Auto-Geofence Creation (Optional Enhancement)

**Prompt:**

```
Implement Phase 10.5: Auto-create geofence from first GPS fix.

1. When a machine receives its first GPS location and no geofence exists:
   - Auto-create a geofence with 100m radius at the first GPS position
   - Name it "Auto-Geofence {machineName}"
   - Set is_active = true
   - Log: "Auto-created geofence for machine={machineId} at {lat},{lng}"

2. This is done in GeofenceBreachService.checkBreach():
   - If no geofence exists for this machine, auto-create one
   - Only auto-create if the machine has no geofence at all (not if it was deleted)
   - Add a flag to the geofences table: is_auto_created BOOLEAN DEFAULT false
   - V45 migration to add the column

3. Admin can edit or delete auto-created geofences
4. Admin can disable auto-creation via a system setting (if needed)

5. Build, deploy, verify auto-creation works on first GPS packet.
```

**Files to modify:**
- `database/migrations/V45__geofence_auto_created.sql` (new)
- `backend/src/main/java/com/yantrago/api/service/GeofenceBreachService.java` (modify)
- `backend/src/main/java/com/yantrago/api/model/Geofence.java` (modify)

---

## Summary: Phase Sequence

| Phase | What | Effort | Deployable? |
|---|---|---|---|
| 9.1 | Backend: Speed metric + MACHINE_MOVING alert + templates | Medium | Yes — backend only |
| 9.2 | Mobile: Movement alert UI | Small | Yes — APK only |
| 10.1 | Backend: Geofence table + CRUD API | Medium | Yes — backend only |
| 10.2 | Backend: Breach detection + templates | Medium | Yes — backend only |
| 10.3 | Admin: Geofence management UI | Medium | Yes — admin web only |
| 10.4 | Mobile: Geofence status + breach UI | Small | Yes — APK only |
| 10.5 | Backend: Auto-geofence creation (optional) | Small | Yes — backend only |

### Recommended Implementation Order

1. **Phase 9.1** → 2. **Phase 9.2** → 3. **Phase 10.1** → 4. **Phase 10.2** → 5. **Phase 10.3** → 6. **Phase 10.4** → 7. **Phase 10.5** (optional)

Each phase is self-contained, deployable, and testable independently.

### Combined Protection

With both approaches active:
- **Speed > 5 km/h** → MACHINE_MOVING alert (instant detection)
- **Outside geo-fence** → GEOFENCE_BREACH alert (location-based)
- **Device offline** → DEVICE_OFFLINE alert (GPS jammed or battery removed)
- **Vibration** → (future: map alarm code 0x02)

Three layers of theft detection ensure the machine can't be moved without triggering at least one alert.
