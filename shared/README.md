# YantraGO Shared Library

Shared Java module consumed by both `backend/` and `device-gateway/` via Gradle's
`project(":shared")` dependency. This guarantees both services compile against the
same RabbitMQ message contracts and shared DTOs — no serialization mismatch is
possible at runtime.

## Contents

### `com.yantrago.shared.queue`
RabbitMQ message contracts (all `Serializable`):
- `QueueNames` — exchange, queue, and routing-key constants
- `CommandMessage` — backend → gateway (issue ON/OFF command)
- `CommandResultMessage` — gateway → backend (command lifecycle status)
- `TelemetryMessage` — gateway → backend (voltage, battery, GSM)
- `DeviceEventMessage` — gateway → backend (LOGIN / HEARTBEAT / DISCONNECT)
- `AlertEventMessage` — alert event contract
- `LocationMessage` — GPS location contract

### `com.yantrago.shared.dto`
- `DeviceStateDto` — last-known device state
- `GpsIngestRequest` — GPS ingest request (Bean Validation)
- `DeviceRecord` — immutable IMEI → device/machine mapping (Java `record`)

### `com.yantrago.shared.util`
- `HashUtil` — SHA-256 hashing utility (not for passwords)

## Build

```bash
./gradlew :shared:build
```

## Reference

- `docs/YANTRAGO_PROJECT_STRUCTURE.md` section 7
