# Battery Level & External Power Status — Implementation Plan

> **Document:** BR05 Protocol Battery/Power Parsing Implementation
> **Project:** YantraGO Machine Management Platform
> **Author:** Engineering
> **Status:** Draft — Pending Approval
> **Date:** 2025-09-11

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Current State Analysis](#2-current-state-analysis)
3. [Protocol Reference](#3-protocol-reference)
4. [Design Goals](#4-design-goals)
5. [Architecture Overview](#5-architecture-overview)
6. [Phase 1 — Protocol Parsing (Device Gateway)](#6-phase-1--protocol-parsing-device-gateway)
7. [Phase 2 — Message Contract (Shared Module)](#7-phase-2--message-contract-shared-module)
8. [Phase 3 — Persistence & Telemetry (Backend)](#8-phase-3--persistence--telemetry-backend)
9. [Phase 4 — API Exposure (Backend REST)](#9-phase-4--api-exposure-backend-rest)
10. [Phase 5 — Mobile UI Display](#10-phase-5--mobile-ui-display)
11. [Phase 6 — Alert Rule Integration](#11-phase-6--alert-rule-integration)
12. [Phase 7 — Tests](#12-phase-7--tests)
13. [Migration Plan](#13-migration-plan)
14. [Risk Analysis](#14-risk-analysis)
15. [File Change Summary](#15-file-change-summary)
16. [Verification Checklist](#16-verification-checklist)

---

## 1. Problem Statement

The BR05 GPS tracker protocol provides battery and external power status through
**three separate mechanisms**, but the current `ConcoxV5ProtocolHandler` only
extracts a **boolean** external-power-connected flag from the GPS positioning
packet. It does **not** parse:

1. The **Voltage Level byte** (7-level internal battery enum) from heartbeat
   and alarm packets.
2. The **GSM signal level byte** from heartbeat and alarm packets.
3. The **alarm codes** for external power events (0x0E, 0x0F, 0x15, 0x19).
4. The **Terminal Information byte** from heartbeat packets (it reads it but
   does not forward the charging status to telemetry).

As a result:
- The `battery_readings` table is always empty (no battery data is ever
  published via `TelemetryMessage`).
- The mobile app's `BatteryWidget` always shows "No report received".
- The `RechargeStatusWidget` always shows "No report received".
- Alert rules on `battery` metric never fire because no battery readings
  are persisted.
- The `TelemetryForwardService` fakes a battery value of `100.0` when
  external power is connected, which is incorrect — it should use the
  actual voltage level from the device.

---

## 2. Current State Analysis

### 2.1 What the Protocol Provides

The BR05 protocol provides battery/power data in **three packet types**:

| Packet | Protocol Number | Has Voltage Level? | Has Terminal Info? | Has GSM Signal? |
|--------|----------------|-------------------|-------------------|-----------------|
| Heartbeat | 0x13 | Yes (1 byte) | Yes (1 byte) | Yes (1 byte) |
| Alarm | 0x26 | Yes (1 byte) | Yes (1 byte) | Yes (1 byte) |
| GPS Positioning | 0x22 | **No** | **No** (only ACC + reporting mode) | **No** |
| LBS Alarm | 0x19 | Yes (1 byte) | Yes (1 byte) | Yes (1 byte) |
| 4G LBS Alarm | 0xA5 | Yes (1 byte) | Yes (1 byte) | Yes (1 byte) |

### 2.2 What the Code Currently Does

#### Heartbeat (0x13) — `handleHeartbeatPacket()`

**Current behavior** (lines 135–164 of `ConcoxV5ProtocolHandler.java`):
- Reads `Terminal Information` byte at `infoOffset`
- Extracts: `accOn`, `charging`, `gpsTracking`, `fuelCutOff`
- Calls `vehicleCommandService.updateLockStateFromHeartbeat()`
- Calls `deviceHeartbeatService.recordHeartbeat()`
- Publishes a `DeviceEventMessage` with `EVENT_HEARTBEAT`

**Missing:**
- Does NOT read the **Voltage Level** byte at `infoOffset + 1`
- Does NOT read the **GSM Signal Level** byte at `infoOffset + 2`
- Does NOT publish a `TelemetryMessage` with battery/voltage/GSM data
- Does NOT forward `charging` status to telemetry

#### GPS Positioning (0x22) — `handleLocationPacket()`

**Current behavior** (lines 295–393 of `ConcoxV5ProtocolHandler.java`):
- Parses date, satellites, lat, lng, speed, course, LBS data
- Reads `ACC` byte at `dataOffset + 26`
- Reads `Terminal Info` byte at `dataOffset + 27` (may be past packet end
  per protocol spec — 0x22 does not include Terminal Info)
- Extracts: `ignitionOn`, `externalPowerConnected`, `sosPressed`,
  `vibrationDetected`, `relayOn`
- Calls `forwardToHttpApi()` which creates a `GpsIngestRequest`

**Problem:** Per the protocol document, the 0x22 packet ends with
`ACC (1 byte) + Reporting Mode (1 byte)`. There is no Terminal Info byte.
The code at line 359 reads `dataOffset + 27` as terminalInfo, which is
likely reading the **reporting mode** byte or past the packet boundary.
The `externalPowerConnected` value from 0x22 is unreliable.

#### Alarm (0x26) — `handleAlarmPacket()`

**Current behavior** (lines 395–400):
```java
private byte[] handleAlarmPacket(byte[] packet, String clientId) {
    log.info("[V5] Alarm packet received from: {}", clientId);
    // Parse alarm type and forward location
    return null;
}
```

**Missing:** The entire alarm packet parser is a stub. It does not parse:
- Date/Time (6 bytes)
- GPS satellites (1 byte)
- Latitude/Longitude (4+4 bytes)
- Speed (1 byte)
- Course/Status (2 bytes)
- LBS data (MCC, MNC, LAC, CellID)
- Terminal Information (1 byte)
- Voltage Level (1 byte)
- GSM Signal Level (1 byte)
- Alarm type/language (2 bytes)

#### TelemetryForwardService — `ingest()`

**Current behavior** (lines 64–75 of `TelemetryForwardService.java`):
```java
TelemetryMessage telemetryMessage = new TelemetryMessage(
    deviceId,
    null, // IMEI
    null, // voltage
    request.getExternalPowerConnected() != null && request.getExternalPowerConnected()
        ? 100.0 : null, // battery approximation — INCORRECT
    request.getGsmSignalStrength() != null
        ? request.getGsmSignalStrength() : null,
    timestamp
);
```

**Problem:** When external power is connected, it fakes `battery = 100.0`.
When external power is disconnected, it sends `battery = null` (no data).
This is not a real battery reading — it's a guess based on charging status.

### 2.3 Database Schema (Already Exists)

The database already has the right tables (migration V7):

```sql
-- voltage_readings: stores voltage (DOUBLE PRECISION)
-- battery_readings: stores battery_pct (DOUBLE PRECISION)
-- gsm_readings: stores gsm_signal (INTEGER)
```

All three are partitioned by month and ready to receive data. The problem
is that no data is being written to `battery_readings` because the gateway
never publishes battery values.

### 2.4 Mobile UI (Already Exists)

The mobile app already has widgets ready to display battery data:

- `BatteryWidget` — expects `int? battery` (percentage 0–100)
- `VoltageWidget` — expects `double? voltage` (volts)
- `RechargeStatusWidget` — expects `bool? charging`

All three show "No report received" when the value is null, which is the
current state because no telemetry data is published.

### 2.5 Alert Rules (Already Exists)

`AlertGenerationService.queryLatestTelemetryValue()` already queries:
- `voltage_readings` for metric `"voltage"`
- `battery_readings` for metric `"battery"`
- `gsm_readings` for metric `"gsm_signal"`

Alert rules can be created with `conditionConfig.metric = "battery"` and
the system will query `battery_readings.battery_pct`. But since no data
is ever written, the query always returns null and no alerts fire.

---

## 3. Protocol Reference

### 3.1 Heartbeat Packet (0x13) — Full Format

```
Offset  Length  Field
0       2       Start bit: 0x78 0x78
2       1       Packet length
3       1       Protocol number: 0x13
4       1       Terminal Information (see bit table below)
5       1       Voltage Level (see enum table below)
6       1       GSM Signal Level (see enum table below)
7       2       Language/Extension port status
9       2       Information sequence number
11      2       Error checking (CRC-ITU)
13      2       Stop bits: 0x0D 0x0A
```

### 3.2 Terminal Information Byte — Bit Definitions

```
Bit  Meaning
7    1: Oil/electricity disconnected | 0: Connected
6    1: GPS positioning | 0: GPS not positioned
3-5  Extension bits (alarm type: 100=SOS, 011=Low battery, 010=Power failure, 001=Vibration, 000=Normal)
2    1: Connected to power and charging | 0: Not connected to power
1    1: ACC high | 0: ACC low
0    1: Fortification (armed) | 0: Disarm
```

### 3.3 Voltage Level Enum (Internal Battery)

```
Value  Meaning                                   Mapped Percentage
0x00   No power (power off)                      0%
0x01   Extremely low (can't make calls/SMS)      10%
0x02   Very low (low battery alarm)              20%
0x03   Low battery (can be used normally)         40%
0x04   Battery level (normal)                     60%
0x05   High battery                               80%
0x06   Extremely high battery                     100%
```

### 3.4 GSM Signal Level Enum

```
Value  Meaning
0x00   No signal
0x01   Extremely weak signal
0x02   Weak signal
0x03   Good signal
0x04   Strong signal
```

### 3.5 Alarm Packet (0x26) — Full Format

```
Offset  Length  Field
0       2       Start bit: 0x78 0x78
2       1       Packet length
3       1       Protocol number: 0x26
4       6       Date and Time (YY MM DD HH MM SS)
10      1       GPS satellite count
11      4       Latitude (divide by 1800000)
15      4       Longitude (divide by 1800000)
19      1       Speed
20      2       Heading/Status
22      1       LBS length
23      2       MCC
25      1       MNC
26      2       LAC
28      3       Cell ID
31      1       Terminal Information
32      1       Voltage Level
33      1       GSM Signal Level
34      2       Alarm type/Language
36      2       Information sequence number
38      2       Error checking (CRC-ITU)
40      2       Stop bits: 0x0D 0x0A
```

### 3.6 Alarm Codes (Byte 1 of Alarm Language)

```
Code  Meaning
0x00  Normal
0x01  SOS
0x02  Power failure alarm
0x03  Vibration alarm
0x04  Fence entry alarm
0x05  Fence-out alarm
0x06  Overspeed alarm
0x09  Displacement alarm
0x0A  Entering GPS blind area
0x0B  GPS blind zone alarm
0x0C  Power-on alarm
0x0D  GPS first positioning alarm
0x0E  External power low alarm
0x0F  External low power protection alarm
0x10  Card replacement alarm
0x11  Shutdown alarm
0x12  Flight mode after external low battery protection
0x13  Disassembly alarm
0x14  Door alarm
0x15  Low power shutdown alarm
0x16  Voice-activated alarm
0x17  Fake base station alarm
0x18  Cover open alarm
0x19  Internal battery low power alarm
0x20  Entering deep sleep alarm
0x23  Fall alarm
0xFF  ACC off
0xFE  ACC on
```

### 3.7 GPS Positioning Packet (0x22) — Full Format

```
Offset  Length  Field
0       2       Start bit: 0x78 0x78
2       1       Packet length
3       1       Protocol number: 0x22
4       6       Date and Time (YY MM DD HH MM SS)
10      1       GPS satellite count
11      4       Latitude (divide by 1800000)
15      4       Longitude (divide by 1800000)
19      1       Speed
20      2       Heading/Status
22      2       MCC
24      1       MNC
25      2       LAC
27      3       Cell ID
30      1       ACC status (0x00=low, 0x01=high)
31      1       Data reporting mode
32      2       Information sequence number
34      2       Error checking (CRC-ITU)
36      2       Stop bits: 0x0D 0x0A
```

**Note:** The 0x22 packet does NOT include Terminal Information, Voltage
Level, or GSM Signal. Battery data is only available from heartbeat (0x13)
and alarm (0x26) packets.

---

## 4. Design Goals

1. **Parse battery level from heartbeat and alarm packets** — extract the
   Voltage Level byte and map it to a percentage.
2. **Parse GSM signal from heartbeat and alarm packets** — extract the GSM
   Signal Level byte.
3. **Parse charging status from Terminal Information byte** — Bit2 indicates
   whether external power is connected.
4. **Publish battery/voltage/GSM telemetry via `TelemetryMessage`** — from
   heartbeat and alarm packets, not just GPS packets.
5. **Remove the fake `100.0` battery approximation** in
   `TelemetryForwardService.ingest()`.
6. **Fix the 0x22 packet parser** — stop reading Terminal Info from a
   position where it doesn't exist. Only use ACC and reporting mode from 0x22.
7. **Implement the alarm packet parser** (0x26) — currently a stub.
8. **Forward alarm codes as device events** — so the backend can generate
   alerts for external power low, low battery shutdown, etc.
9. **No breaking changes** — existing GPS/location flow must continue to work.
10. **Tests** — unit tests for all new parsing logic.

---

## 5. Architecture Overview

```
Device (BR05)
    │
    │ TCP packets: 0x13 (heartbeat), 0x22 (GPS), 0x26 (alarm)
    ▼
ConcoxV5ProtocolHandler
    │
    ├── handleHeartbeatPacket()
    │     ├── Parse Terminal Info byte → charging, acc, fortification
    │     ├── Parse Voltage Level byte → battery percentage (NEW)
    │     ├── Parse GSM Signal byte → signal strength (NEW)
    │     └── Publish TelemetryMessage (battery, gsmSignal) (NEW)
    │
    ├── handleLocationPacket() (0x22)
    │     ├── Parse GPS data (lat, lng, speed, course, time)
    │     ├── Parse ACC byte → ignition status
    │     ├── FIX: Remove Terminal Info parsing (not in 0x22)
    │     └── Forward GpsIngestRequest (location only, no battery)
    │
    └── handleAlarmPacket() (0x26) — FULLY IMPLEMENT (NEW)
          ├── Parse GPS data (same as 0x22)
          ├── Parse Terminal Info byte → charging, sos, vibration
          ├── Parse Voltage Level byte → battery percentage
          ├── Parse GSM Signal byte → signal strength
          ├── Parse Alarm code → alarm type
          ├── Publish TelemetryMessage (battery, gsmSignal)
          ├── Publish LocationMessage (if GPS located)
          └── Publish DeviceEventMessage (alarm type)
                │
                ▼
          TelemetryForwardService
                │
                ├── TelemetryMessage → RabbitMQ → TelemetryConsumer
                │     → battery_readings, voltage_readings, gsm_readings
                │     → AlertGenerationService (battery rules)
                │
                ├── LocationMessage → RabbitMQ → LocationConsumer
                │     → location_history
                │
                └── DeviceEventMessage → RabbitMQ → DeviceEventConsumer
                      → Alert generation (alarm codes)
```

---

## 6. Phase 1 — Protocol Parsing (Device Gateway)

### 1.1 Add Voltage Level Mapping Utility

**File:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/BatteryLevelMapper.java` (NEW)

```java
package com.yantrago.gateway.tcp.concox;

/**
 * Maps the BR05 protocol's 7-level voltage enum to a battery percentage.
 *
 * The BR05 protocol does NOT provide a numeric battery percentage.
 * It provides a coarse 7-level enum (0x00–0x06) representing the
 * internal backup battery state. This mapper converts that enum to
 * an approximate percentage for display and alert purposes.
 *
 * Per AGENTS.md rule 13: protocol parsing follows the BR05 spec as-is.
 * This mapping is an application-level interpretation, not protocol parsing.
 */
public final class BatteryLevelMapper {

    private BatteryLevelMapper() {}

    /**
     * Maps the BR05 voltage level byte to an approximate battery percentage.
     *
     * @param voltageLevel the raw byte value (0x00–0x06)
     * @return battery percentage (0–100), or null if the value is invalid
     */
    public static Integer toPercentage(int voltageLevel) {
        return switch (voltageLevel) {
            case 0x00 -> 0;   // No power (power off)
            case 0x01 -> 10;  // Extremely low
            case 0x02 -> 20;  // Very low (low battery alarm)
            case 0x03 -> 40;  // Low battery (usable)
            case 0x04 -> 60;  // Normal
            case 0x05 -> 80;  // High
            case 0x06 -> 100; // Extremely high
            default -> null;   // Invalid value
        };
    }

    /**
     * Maps the BR05 GSM signal level byte to a numeric signal strength.
     *
     * @param gsmLevel the raw byte value (0x00–0x04)
     * @return signal strength (0–4), or null if invalid
     */
    public static Integer toGsmSignal(int gsmLevel) {
        if (gsmLevel >= 0x00 && gsmLevel <= 0x04) {
            return gsmLevel;
        }
        return null;
    }
}
```

### 1.2 Update GpsIngestRequest (Gateway Model)

**File:** `device-gateway/src/main/java/com/yantrago/gateway/model/GpsIngestRequest.java`

**Add fields:**
```java
private Integer batteryLevel;    // Internal battery percentage (0–100)
private Integer batteryVoltage;  // Raw voltage level enum (0x00–0x06)
private Boolean externalPowerConnected; // Already exists
```

**Rationale:** The gateway model needs to carry battery data from the
protocol handler to `TelemetryForwardService`. Currently, only
`externalPowerConnected` is carried, and battery is faked as 100.0.

### 1.3 Update handleHeartbeatPacket()

**File:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandler.java`

**Current** (lines 135–164): Reads Terminal Info byte only.

**New behavior:**
```java
private byte[] handleHeartbeatPacket(byte[] packet, String clientId) {
    log.info("[V5] Heartbeat from: {}", clientId);
    boolean extended = packet[0] == 0x79;
    int infoOffset = extended ? 5 : 4;

    if (packet.length > infoOffset) {
        int terminalInfo = packet[infoOffset] & 0xFF;
        boolean accOn = (terminalInfo & 0x02) != 0;
        boolean charging = (terminalInfo & 0x04) != 0;
        boolean gpsTracking = (terminalInfo & 0x40) != 0;
        boolean fuelCutOff = (terminalInfo & 0x80) != 0;

        // NEW: Parse Voltage Level byte (infoOffset + 1)
        Integer batteryPct = null;
        if (packet.length > infoOffset + 1) {
            int voltageLevel = packet[infoOffset + 1] & 0xFF;
            batteryPct = BatteryLevelMapper.toPercentage(voltageLevel);
        }

        // NEW: Parse GSM Signal Level byte (infoOffset + 2)
        Integer gsmSignal = null;
        if (packet.length > infoOffset + 2) {
            int gsmLevel = packet[infoOffset + 2] & 0xFF;
            gsmSignal = BatteryLevelMapper.toGsmSignal(gsmLevel);
        }

        log.debug("[V5] Heartbeat: ACC={}, Charging={}, GPS={}, FuelCut={}, Battery={}%, GSM={}",
            accOn ? "ON" : "OFF", charging ? "Yes" : "No",
            gpsTracking ? "ON" : "OFF", fuelCutOff ? "YES" : "NO",
            batteryPct, gsmSignal);

        String imei = clientImeiMap.get(clientId);
        if (imei != null) {
            try {
                vehicleCommandService.updateLockStateFromHeartbeat(imei, fuelCutOff);
                deviceHeartbeatService.recordHeartbeat(imei);
                publishDeviceEvent(imei, DeviceEventMessage.EVENT_HEARTBEAT);

                // NEW: Forward battery/GSM telemetry from heartbeat
                forwardTelemetryFromHeartbeat(imei, batteryPct, gsmSignal, charging);
            } catch (Exception e) {
                log.error("[V5] Failed to process heartbeat: {}", e.getMessage());
            }
        }
    }
    return buildResponse(packet, (byte) 0x13);
}
```

### 1.4 Add forwardTelemetryFromHeartbeat() Method

**File:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandler.java`

```java
/**
 * Forwards battery and GSM telemetry extracted from a heartbeat packet
 * to the backend via RabbitMQ. Heartbeats are the primary source of
 * battery level data since the 0x22 GPS packet does not include it.
 */
private void forwardTelemetryFromHeartbeat(String imei, Integer batteryPct,
        Integer gsmSignal, boolean charging) {
    try {
        String deviceId = deviceMappingCacheService.getDeviceIdByImei(imei);
        if (deviceId == null) {
            log.warn("[V5] No device mapping for IMEI: {}, skipping telemetry", imei);
            return;
        }
        UUID deviceUuid = UUID.fromString(deviceId);

        // Publish telemetry message with battery and GSM data
        // voltage is null (BR05 doesn't provide voltage in volts, only level enum)
        // battery is the mapped percentage
        TelemetryMessage msg = new TelemetryMessage(
            deviceUuid, imei, null, batteryPct != null ? batteryPct.doubleValue() : null,
            gsmSignal, Instant.now()
        );
        telemetryProducer.publishTelemetry(msg);

        log.debug("[V5] Forwarded heartbeat telemetry: imei={} battery={}%, gsm={}",
            imei, batteryPct, gsmSignal);
    } catch (Exception e) {
        log.error("[V5] Failed to forward heartbeat telemetry: {}", e.getMessage());
    }
}
```

**Note:** This requires adding `TelemetryProducer` as a dependency to
`ConcoxV5ProtocolHandler`. Currently, the handler depends on
`GpsIngestService` (which is `TelemetryForwardService`). We need to
either:
- Add `TelemetryProducer` as a direct dependency, OR
- Add a method to `GpsIngestService` interface for telemetry-only forwarding

**Recommended:** Add a `forwardTelemetry()` method to `GpsIngestService`
interface to maintain the existing abstraction.

### 1.5 Fix handleLocationPacket() (0x22)

**File:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandler.java`

**Current problem:** Lines 358–366 read `terminalInfo` from `dataOffset + 27`,
but per the protocol spec, the 0x22 packet ends with `ACC (1 byte) +
Reporting Mode (1 byte)`. There is no Terminal Info byte in 0x22.

**Fix:**
- Remove the Terminal Info parsing from `handleLocationPacket()`
- Only parse ACC byte (ignition on/off) from the 0x22 packet
- Set `externalPowerConnected`, `sosPressed`, `vibrationDetected`, `relayOn`
  to `null` or `false` — these are NOT available from 0x22
- Battery/voltage data will come from heartbeat packets instead

```java
// ACC status
int acc = packet[dataOffset + 26] & 0xFF;
boolean ignitionOn = (acc & 0x01) != 0;

// REMOVED: Terminal Info parsing — 0x22 does not include this byte
// Battery, charging, SOS, vibration, relay status are only available
// from heartbeat (0x13) and alarm (0x26) packets.

// Forward to API with location data only
if (gpsLocated) {
    String imei = clientImeiMap.get(clientId);
    forwardToHttpApi(latitude, longitude, speed, course,
        year, month, day, hour, minute, second, imei,
        ignitionOn, null, null, null, null,  // no terminal info from 0x22
        satelliteCount, mcc, mnc);
}
```

### 1.6 Implement handleAlarmPacket() (0x26)

**File:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandler.java`

**Current:** Stub that just logs and returns null.

**New behavior:** Full alarm packet parser:

```java
private byte[] handleAlarmPacket(byte[] packet, String clientId) {
    log.info("[V5] Alarm packet received from: {}", clientId);
    try {
        boolean extended = packet[0] == 0x79;
        int dataOffset = extended ? 5 : 4;

        // Date Time: 6 bytes
        int year = packet[dataOffset] & 0xFF;
        int month = packet[dataOffset + 1] & 0xFF;
        int day = packet[dataOffset + 2] & 0xFF;
        int hour = packet[dataOffset + 3] & 0xFF;
        int minute = packet[dataOffset + 4] & 0xFF;
        int second = packet[dataOffset + 5] & 0xFF;

        // GPS Satellites: 1 byte
        int satelliteCount = packet[dataOffset + 6] & 0x0F;

        // Latitude: 4 bytes
        int latRaw = ((packet[dataOffset + 7] & 0xFF) << 24) |
                     ((packet[dataOffset + 8] & 0xFF) << 16) |
                     ((packet[dataOffset + 9] & 0xFF) << 8) |
                     (packet[dataOffset + 10] & 0xFF);

        // Longitude: 4 bytes
        int lngRaw = ((packet[dataOffset + 11] & 0xFF) << 24) |
                     ((packet[dataOffset + 12] & 0xFF) << 16) |
                     ((packet[dataOffset + 13] & 0xFF) << 8) |
                     (packet[dataOffset + 14] & 0xFF);

        // Speed: 1 byte
        int speed = packet[dataOffset + 15] & 0xFF;

        // Course & Status: 2 bytes
        int courseStatus = ((packet[dataOffset + 16] & 0xFF) << 8) |
                           (packet[dataOffset + 17] & 0xFF);
        int course = courseStatus & 0x03FF;
        boolean gpsLocated = (courseStatus & 0x0400) != 0;
        boolean eastLongitude = (courseStatus & 0x0800) == 0;
        boolean northLatitude = (courseStatus & 0x1000) != 0;

        double latitude = latRaw / 1800000.0;
        double longitude = lngRaw / 1800000.0;
        if (!northLatitude) latitude = -latitude;
        if (!eastLongitude) longitude = -longitude;

        // LBS Data
        int lbsLength = packet[dataOffset + 18] & 0xFF;
        int mcc = ((packet[dataOffset + 19] & 0xFF) << 8) |
                  (packet[dataOffset + 20] & 0xFF);
        int mnc = packet[dataOffset + 21] & 0xFF;
        int lac = ((packet[dataOffset + 22] & 0xFF) << 8) |
                  (packet[dataOffset + 23] & 0xFF);
        int cellId = ((packet[dataOffset + 24] & 0xFF) << 16) |
                     ((packet[dataOffset + 25] & 0xFF) << 8) |
                     (packet[dataOffset + 26] & 0xFF);

        // Terminal Information: 1 byte (at dataOffset + 27)
        int terminalInfo = packet[dataOffset + 27] & 0xFF;
        boolean ignitionOn = (terminalInfo & 0x02) != 0;
        boolean charging = (terminalInfo & 0x04) != 0;
        boolean sosPressed = (terminalInfo & 0x01) != 0;
        boolean vibrationDetected = (terminalInfo & 0x08) != 0;
        boolean relayOn = (terminalInfo & 0x80) != 0;

        // Voltage Level: 1 byte (at dataOffset + 28)
        int voltageLevel = packet[dataOffset + 28] & 0xFF;
        Integer batteryPct = BatteryLevelMapper.toPercentage(voltageLevel);

        // GSM Signal Level: 1 byte (at dataOffset + 29)
        int gsmLevel = packet[dataOffset + 29] & 0xFF;
        Integer gsmSignal = BatteryLevelMapper.toGsmSignal(gsmLevel);

        // Alarm type/language: 2 bytes (at dataOffset + 30)
        int alarmCode = packet[dataOffset + 30] & 0xFF;
        int language = packet[dataOffset + 31] & 0xFF;

        log.info("[V5] Alarm: code=0x{}, Battery={}%, GSM={}, Charging={}, GPS={}, SOS={}, Vibration={}",
            String.format("%02X", alarmCode), batteryPct, gsmSignal,
            charging ? "Yes" : "No", gpsLocated ? "Yes" : "No",
            sosPressed ? "YES" : "no", vibrationDetected ? "YES" : "no");

        String imei = clientImeiMap.get(clientId);
        if (imei != null) {
            // Forward telemetry (battery + GSM)
            forwardTelemetryFromHeartbeat(imei, batteryPct, gsmSignal, charging);

            // Forward location if GPS located
            if (gpsLocated) {
                forwardToHttpApi(latitude, longitude, speed, course,
                    year, month, day, hour, minute, second, imei,
                    ignitionOn, charging, sosPressed, vibrationDetected, relayOn,
                    satelliteCount, mcc, mnc);
            }

            // Publish alarm event
            publishAlarmEvent(imei, alarmCode);
        }

        return buildResponse(packet, (byte) 0x26);
    } catch (Exception e) {
        log.error("[V5] Error parsing alarm packet", e);
        return null;
    }
}
```

### 1.7 Add publishAlarmEvent() Method

**File:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandler.java`

```java
/**
 * Publishes an alarm event to the backend via RabbitMQ.
 * The alarm code is forwarded as-is so the backend can map it to
 * alert types (e.g. 0x0E → EXTERNAL_POWER_LOW, 0x19 → LOW_BATTERY).
 */
private void publishAlarmEvent(String imei, int alarmCode) {
    try {
        String deviceId = deviceMappingCacheService.getDeviceIdByImei(imei);
        if (deviceId == null) return;

        DeviceEventMessage event = new DeviceEventMessage(
            UUID.fromString(deviceId), imei,
            "ALARM:" + String.format("0x%02X", alarmCode),
            Instant.now()
        );
        deviceEventProducer.publishDeviceEvent(event);
        log.info("[V5] Published alarm event: imei={} code=0x{}", imei,
            String.format("%02X", alarmCode));
    } catch (Exception e) {
        log.error("[V5] Failed to publish alarm event: {}", e.getMessage());
    }
}
```

---

## 7. Phase 2 — Message Contract (Shared Module)

### 2.1 Add Charging Status to TelemetryMessage

**File:** `shared/src/main/java/com/yantrago/shared/queue/TelemetryMessage.java`

**Add field:**
```java
private Boolean charging;  // true = external power connected, false = on battery
```

**Rationale:** The `TelemetryConsumer` needs to know the charging status to:
1. Store it in `device_state` for the mobile app's `RechargeStatusWidget`.
2. Distinguish "battery 60% and charging" from "battery 60% and discharging".

### 2.2 Add Alarm Code to DeviceEventMessage

**File:** `shared/src/main/java/com/yantrago/shared/queue/DeviceEventMessage.java`

**Verify:** The existing `eventType` field can carry `"ALARM:0x0E"` format.
If not, add a dedicated `alarmCode` field:

```java
private Integer alarmCode;  // BR05 alarm code (0x00–0x23), null for non-alarm events
```

---

## 8. Phase 3 — Persistence & Telemetry (Backend)

### 3.1 Update TelemetryConsumer

**File:** `backend/src/main/java/com/yantrago/api/queue/TelemetryConsumer.java`

**Current:** Persists voltage, battery, and GSM readings from
`TelemetryMessage`. Does not persist charging status.

**Changes:**
- Add persistence of `charging` status to `device_state` table (or a new
  `device_telemetry_state` table).
- The existing battery/voltage/GSM persistence is already correct — it
  just needs data to arrive (which it will after Phase 1).

### 3.2 Add Charging Status to Device State

**Option A:** Add columns to `devices` table:
```sql
ALTER TABLE devices
    ADD COLUMN battery_pct DOUBLE PRECISION,
    ADD COLUMN charging BOOLEAN,
    ADD COLUMN gsm_signal INTEGER,
    ADD COLUMN last_telemetry_at TIMESTAMPTZ;
```

**Option B:** Use a separate `device_state` table (already may exist via
`DeviceStateDto`). If not, create one:
```sql
CREATE TABLE IF NOT EXISTS device_state (
    device_id         UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL,
    battery_pct       DOUBLE PRECISION,
    charging          BOOLEAN,
    gsm_signal        INTEGER,
    voltage           DOUBLE PRECISION,
    last_updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Recommended:** Option A (add columns to `devices`) is simpler and avoids
a join. The `devices` table already has `last_seen_at` — adding battery
columns is consistent.

### 3.3 Update TelemetryConsumer to Persist Device State

```java
// After persisting time-series readings, update device state
if (message.getBattery() != null || message.getCharging() != null) {
    jdbcTemplate.update(
        "UPDATE devices SET battery_pct = ?, charging = ?, " +
        "gsm_signal = ?, last_telemetry_at = ?, updated_at = now() " +
        "WHERE id = ?",
        message.getBattery(), message.getCharging(),
        message.getGsmSignal(), LocalDateTime.now(),
        message.getDeviceId()
    );
}
```

### 3.4 Handle Alarm Events in DeviceEventConsumer

**File:** `backend/src/main/java/com/yantrago/api/queue/DeviceEventConsumer.java`

**Current:** Handles heartbeat and online/offline events.

**Add:** Map BR05 alarm codes to alert types:

```java
private static final Map<Integer, String> ALARM_CODE_MAP = Map.of(
    0x0E, "EXTERNAL_POWER_LOW",
    0x0F, "EXTERNAL_POWER_PROTECTION",
    0x15, "LOW_POWER_SHUTDOWN",
    0x19, "INTERNAL_BATTERY_LOW"
);

// In handleDeviceEvent():
if (event.getEventType() != null && event.getEventType().startsWith("ALARM:")) {
    String hexCode = event.getEventType().substring(5);
    int alarmCode = Integer.parseInt(hexCode, 16);
    String alertType = ALARM_CODE_MAP.get(alarmCode);
    if (alertType != null) {
        // Generate alert via CanonicalAlertService
        canonicalAlertService.processAlertEvent(
            machineId, alertType, "WARNING", message,
            Instant.now(), null, null, null
        );
    }
}
```

---

## 9. Phase 4 — API Exposure (Backend REST)

### 4.1 Add Battery/Charging Fields to Machine Detail DTO

**File:** `backend/src/main/java/com/yantrago/api/dto/machine/MachineDetailDto.java`

**Add fields:**
```java
private Integer batteryPct;       // 0–100, null if no data
private Boolean charging;         // true = on external power
private Integer gsmSignal;        // 0–4, null if no data
private Instant lastTelemetryAt;   // when last telemetry was received
```

### 4.2 Populate Battery Fields in MachineService

**File:** `backend/src/main/java/com/yantrago/api/service/MachineService.java`

When fetching machine details, join with `devices` table to get
`battery_pct`, `charging`, `gsm_signal`, `last_telemetry_at`.

### 4.3 Add Telemetry Endpoint (If Not Exists)

**Endpoint:** `GET /api/v1/machines/{id}/telemetry/latest`

**Response:**
```json
{
  "batteryPct": 60,
  "charging": true,
  "gsmSignal": 3,
  "voltage": null,
  "lastTelemetryAt": "2025-09-11T12:00:00Z"
}
```

---

## 10. Phase 5 — Mobile UI Display

### 5.1 Update Telemetry Model

**File:** `mobile/lib/models/telemetry.dart`

**Current:** Already has `battery`, `voltage`, `gsmSignal`, `charging`.

**Verify:** The `fromJson` correctly maps `batteryPct` → `battery` and
`charging` → `charging`. May need to update field names to match backend
DTO.

### 5.2 Update Machine Telemetry Provider

**File:** `mobile/lib/features/machines/providers/machine_telemetry_provider.dart`

**Verify:** The provider fetches from the telemetry endpoint and maps
the response to the `Telemetry` model.

### 5.3 Verify Dashboard Widgets

**Files:**
- `mobile/lib/features/dashboard/widgets/battery_widget.dart` — Already
  handles `int? battery` with thresholds (60/20). No change needed.
- `mobile/lib/features/dashboard/widgets/voltage_widget.dart` — Already
  handles `double? voltage`. No change needed.
- `mobile/lib/features/dashboard/widgets/recharge_status_widget.dart` —
  Already handles `bool? charging`. No change needed.

**No mobile UI changes required** — the widgets are already built. They
just need data to arrive from the backend, which will happen once the
protocol parsing is implemented.

---

## 11. Phase 6 — Alert Rule Integration

### 11.1 Battery Alert Rules

Once battery data is flowing into `battery_readings`, existing alert rules
will work automatically:

```
conditionConfig: {"metric":"battery","operator":"LT","threshold":20.0}
```

This will query `battery_readings.battery_pct` and fire when battery < 20%.

### 11.2 New Alert Types from Alarm Codes

Add new alert types to the alert type catalog:

| Alert Type | Alarm Code | Severity | Description |
|------------|-----------|----------|-------------|
| `EXTERNAL_POWER_LOW` | 0x0E | WARNING | External power voltage dropped below threshold |
| `EXTERNAL_POWER_CUT` | 0x0F | CRITICAL | External power protection triggered (imminent shutdown) |
| `LOW_POWER_SHUTDOWN` | 0x15 | CRITICAL | Device shutting down due to low battery |
| `INTERNAL_BATTERY_LOW` | 0x19 | WARNING | Internal backup battery is low |

### 11.3 Notification Templates

Add notification templates for the new alert types in the next migration:

```sql
INSERT INTO notification_templates (id, event_type, alert_type, incident_state, title_template, body_template, ...)
VALUES
  (..., 'ALERT_TRANSITION', 'EXTERNAL_POWER_LOW', 'OPEN', 'External Power Low', 'Machine {machineName} external power is low', ...),
  (..., 'ALERT_TRANSITION', 'EXTERNAL_POWER_CUT', 'OPEN', 'External Power Cut', 'Machine {machineName} external power has been cut', ...),
  (..., 'ALERT_TRANSITION', 'LOW_POWER_SHUTDOWN', 'OPEN', 'Low Power Shutdown', 'Machine {machineName} is shutting down due to low battery', ...),
  (..., 'ALERT_TRANSITION', 'INTERNAL_BATTERY_LOW', 'OPEN', 'Internal Battery Low', 'Machine {machineName} internal battery is low', ...)
ON CONFLICT DO NOTHING;
```

---

## 12. Phase 7 — Tests

### 12.1 BatteryLevelMapper Test

**File:** `device-gateway/src/test/java/com/yantrago/gateway/tcp/concox/BatteryLevelMapperTest.java` (NEW)

Test cases:
- `toPercentage(0x00)` → 0
- `toPercentage(0x01)` → 10
- `toPercentage(0x06)` → 100
- `toPercentage(0x07)` → null (invalid)
- `toGsmSignal(0x00)` → 0
- `toGsmSignal(0x04)` → 4
- `toGsmSignal(0x05)` → null (invalid)

### 12.2 Heartbeat Packet Test

**File:** `device-gateway/src/test/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandlerTest.java`

Add test:
- Construct a heartbeat packet with Terminal Info=0x04 (charging),
  Voltage Level=0x04 (60%), GSM=0x03 (good)
- Call `handlePacket()`
- Verify `TelemetryProducer.publishTelemetry()` is called with
  `battery=60.0`, `gsmSignal=3`
- Verify `deviceHeartbeatService.recordHeartbeat()` is called

### 12.3 Alarm Packet Test

**File:** `device-gateway/src/test/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandlerTest.java`

Add test:
- Construct an alarm packet (0x26) with alarm code 0x0E (external power low)
- Call `handlePacket()`
- Verify `DeviceEventProducer.publishDeviceEvent()` is called with
  event type `"ALARM:0x0E"`
- Verify telemetry is forwarded with battery and GSM data

### 12.4 GPS Packet Fix Test

**File:** `device-gateway/src/test/java/com/yantrago/gateway/tcp/concox/ConcoxV5ProtocolHandlerTest.java`

Add test:
- Construct a 0x22 GPS packet with only ACC + reporting mode (no terminal info)
- Call `handlePacket()`
- Verify no exception is thrown
- Verify `externalPowerConnected` is NOT set (null/false)

### 12.5 Backend Telemetry Consumer Test

**File:** `backend/src/test/java/com/yantrago/api/queue/TelemetryConsumerTest.java`

Add test:
- Send a `TelemetryMessage` with `battery=60.0`, `charging=true`, `gsmSignal=3`
- Verify `battery_readings` row is inserted
- Verify `devices` table is updated with `battery_pct=60`, `charging=true`

### 12.6 Alarm Event Consumer Test

**File:** `backend/src/test/java/com/yantrago/api/queue/DeviceEventConsumerTest.java`

Add test:
- Send a `DeviceEventMessage` with `eventType="ALARM:0x0E"`
- Verify `CanonicalAlertService.processAlertEvent()` is called with
  `alertType="EXTERNAL_POWER_LOW"`

---

## 13. Migration Plan

### 13.1 Database Migration

**File:** `database/migrations/V33__device_battery_state.sql` (NEW)

```sql
-- V33: Add battery/charging/GSM state columns to devices table.
-- These columns store the LATEST known state from heartbeat/alarm packets.
-- Time-series history remains in battery_readings, voltage_readings, gsm_readings.

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS battery_pct DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS charging BOOLEAN,
    ADD COLUMN IF NOT EXISTS gsm_signal INTEGER,
    ADD COLUMN IF NOT EXISTS last_telemetry_at TIMESTAMPTZ;

COMMENT ON COLUMN devices.battery_pct IS 'Latest internal battery percentage (0-100) from heartbeat/alarm voltage level byte.';
COMMENT ON COLUMN devices.charging IS 'True when external power is connected (Terminal Info Bit2).';
COMMENT ON COLUMN devices.gsm_signal IS 'Latest GSM signal level (0-4) from heartbeat/alarm.';
COMMENT ON COLUMN devices.last_telemetry_at IS 'When the last telemetry-bearing packet (heartbeat/alarm) was received.';
```

### 13.2 Notification Templates Migration

**File:** `database/migrations/V34__alarm_code_templates.sql` (NEW)

```sql
-- V34: Notification templates for BR05 alarm-code-driven alert types.

INSERT INTO notification_templates (id, event_type, alert_type, incident_state, title_template, body_template, created_at)
SELECT gen_random_uuid(), 'ALERT_TRANSITION', 'EXTERNAL_POWER_LOW', 'OPEN',
       'External Power Low', 'Machine {machineName} external power is low', now()
WHERE NOT EXISTS (SELECT 1 FROM notification_templates WHERE alert_type = 'EXTERNAL_POWER_LOW' AND incident_state = 'OPEN')
ON CONFLICT DO NOTHING;

-- Repeat for EXTERNAL_POWER_CUT, LOW_POWER_SHUTDOWN, INTERNAL_BATTERY_LOW
```

### 13.3 Rollout Sequence

1. Apply migration V33 (add device columns)
2. Apply migration V34 (add templates)
3. Deploy device-gateway with new protocol parsing
4. Deploy backend with updated TelemetryConsumer and DeviceEventConsumer
5. Deploy mobile app (no change needed — widgets already exist)
6. Verify heartbeat packets populate `battery_pct` and `charging`
7. Verify alarm packets generate alerts

---

## 14. Risk Analysis

| Risk | Impact | Mitigation |
|------|--------|------------|
| 0x22 packet parser currently reads past packet boundary | Could cause `ArrayIndexOutOfBoundsException` on some devices | Fix in Phase 1.5 — remove Terminal Info parsing from 0x22 |
| Battery percentage is a coarse 7-level enum, not precise | UI shows 0/10/20/40/60/80/100 — no granular values | Document in code comments; acceptable for monitoring use case |
| Heartbeat interval may be long (e.g. 5 min) | Battery data may be stale between heartbeats | Show `lastTelemetryAt` in UI so user knows data freshness |
| Alarm packet format may vary by firmware version | Parser may fail on some devices | Add length checks before each field access; log and skip on parse errors |
| Adding `TelemetryProducer` dependency to `ConcoxV5ProtocolHandler` | Changes constructor — may affect tests | Update test mocks in Phase 7 |
| `TelemetryForwardService.ingest()` currently fakes battery=100.0 | Removing this may break existing behavior | Replace with null — let heartbeat provide real battery data |
| Device may not send heartbeat frequently | Battery widget shows "No report received" for extended periods | Acceptable — this is honest behavior, not a bug |

---

## 15. File Change Summary

### New Files

| File | Purpose |
|------|---------|
| `device-gateway/.../concox/BatteryLevelMapper.java` | Voltage level enum → percentage mapping |
| `device-gateway/.../concox/BatteryLevelMapperTest.java` | Unit tests for mapper |
| `database/migrations/V33__device_battery_state.sql` | Add battery columns to devices |
| `database/migrations/V34__alarm_code_templates.sql` | Notification templates for alarm codes |

### Modified Files

| File | Changes |
|------|---------|
| `device-gateway/.../concox/ConcoxV5ProtocolHandler.java` | Parse voltage/GSM from heartbeat; implement alarm parser; fix 0x22 parser; add `TelemetryProducer` dependency |
| `device-gateway/.../model/GpsIngestRequest.java` | Add `batteryLevel`, `batteryVoltage` fields |
| `device-gateway/.../service/TelemetryForwardService.java` | Remove fake `100.0` battery; use real battery from heartbeat |
| `device-gateway/.../service/GpsIngestService.java` | Add `forwardTelemetry()` method for telemetry-only forwarding |
| `shared/.../queue/TelemetryMessage.java` | Add `charging` field |
| `shared/.../queue/DeviceEventMessage.java` | Add `alarmCode` field (if needed) |
| `backend/.../queue/TelemetryConsumer.java` | Persist `charging` to `devices` table |
| `backend/.../queue/DeviceEventConsumer.java` | Map alarm codes to alert types |
| `backend/.../dto/machine/MachineDetailDto.java` | Add battery/charging/gsm fields |
| `backend/.../service/MachineService.java` | Populate battery fields from devices table |
| `device-gateway/.../concox/ConcoxV5ProtocolHandlerTest.java` | Add heartbeat battery, alarm packet, 0x22 fix tests |
| `backend/.../queue/TelemetryConsumerTest.java` | Add battery/charging persistence test |
| `backend/.../queue/DeviceEventConsumerTest.java` | Add alarm code mapping test |

### Unchanged Files (Verified Compatible)

| File | Why No Change Needed |
|------|----------------------|
| `mobile/lib/features/dashboard/widgets/battery_widget.dart` | Already handles `int? battery` |
| `mobile/lib/features/dashboard/widgets/voltage_widget.dart` | Already handles `double? voltage` |
| `mobile/lib/features/dashboard/widgets/recharge_status_widget.dart` | Already handles `bool? charging` |
| `mobile/lib/models/telemetry.dart` | Already has battery/voltage/gsm/charging fields |
| `database/migrations/V7__create_telemetry_tables.sql` | Tables already exist and are empty (ready for data) |
| `backend/.../service/AlertGenerationService.java` | Already queries `battery_readings` — will work once data arrives |

---

## 16. Verification Checklist

### Phase 1 (Protocol Parsing)
- [ ] `BatteryLevelMapper.toPercentage()` maps all 7 levels correctly
- [ ] `handleHeartbeatPacket()` parses voltage level and GSM signal
- [ ] `handleHeartbeatPacket()` publishes `TelemetryMessage` with battery data
- [ ] `handleAlarmPacket()` fully parses alarm packet (0x26)
- [ ] `handleAlarmPacket()` extracts alarm code and publishes device event
- [ ] `handleLocationPacket()` (0x22) no longer reads Terminal Info
- [ ] No `ArrayIndexOutOfBoundsException` on short packets

### Phase 2 (Message Contract)
- [ ] `TelemetryMessage` has `charging` field
- [ ] `DeviceEventMessage` can carry alarm codes

### Phase 3 (Persistence)
- [ ] `TelemetryConsumer` persists battery to `battery_readings`
- [ ] `TelemetryConsumer` updates `devices.battery_pct` and `devices.charging`
- [ ] `DeviceEventConsumer` maps alarm codes to alert types
- [ ] Alert rules on `battery` metric fire when data arrives

### Phase 4 (API)
- [ ] `GET /api/v1/machines/{id}` returns `batteryPct`, `charging`, `gsmSignal`
- [ ] `GET /api/v1/machines/{id}/telemetry/latest` returns latest telemetry

### Phase 5 (Mobile)
- [ ] `BatteryWidget` shows percentage (not "No report received")
- [ ] `RechargeStatusWidget` shows "Charging" or "On battery"
- [ ] `VoltageWidget` shows voltage or "No report received" (BR05 has no voltage in volts)

### Phase 6 (Alerts)
- [ ] Battery < 20% rule fires `LOW_BATTERY` alert
- [ ] Alarm code 0x0E fires `EXTERNAL_POWER_LOW` alert
- [ ] Alarm code 0x19 fires `INTERNAL_BATTERY_LOW` alert
- [ ] Notification inbox receives entries for battery alerts

### Phase 7 (Tests)
- [ ] `BatteryLevelMapperTest` passes (7+ test cases)
- [ ] `ConcoxV5ProtocolHandlerTest` heartbeat battery test passes
- [ ] `ConcoxV5ProtocolHandlerTest` alarm packet test passes
- [ ] `ConcoxV5ProtocolHandlerTest` 0x22 fix test passes
- [ ] `TelemetryConsumerTest` battery persistence test passes
- [ ] `DeviceEventConsumerTest` alarm code mapping test passes
- [ ] Full backend test suite passes (260+ tests)
- [ ] Full mobile test suite passes (173+ tests)
- [ ] Full gateway test suite passes

### Migration
- [ ] V33 migration applies cleanly
- [ ] V34 migration applies cleanly
- [ ] No data loss on existing devices

---

## Appendix: Battery Level Mapping Reference

```
Protocol Byte   Enum Label              Mapped %    UI Status
0x00           No power (off)           0%          Critical (red)
0x01           Extremely low            10%         Critical (red)
0x02           Very low (alarm)         20%         Danger (red)
0x03           Low (usable)             40%         Warning (amber)
0x04           Normal                   60%         Healthy (green)
0x05           High                     80%         Healthy (green)
0x06           Extremely high           100%        Healthy (green)
```

**Note:** These are approximate mappings for the internal backup battery.
The external power status is a separate boolean (Terminal Info Bit2).
When external power is connected, the device charges the internal battery,
so `battery_pct` may show 60–100% while `charging=true`.
