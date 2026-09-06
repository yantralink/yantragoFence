# YantraGO Device Gateway

TCP device gateway for the YantraGO platform. Handles raw TCP connections from GPS/fencing devices, parses protocol packets, and forwards data to the backend API via RabbitMQ.

## Protocols Supported

| Protocol | Port | Devices |
|----------|------|---------|
| Concox V5 / BR05 | 5000 | GPS trackers (Coban, Concox) |
| JT808-2013 | 5001 | T98 dashcams |
| Fencing | 5002 | YantraGO fencing machines |

## Architecture

The gateway is a separate Spring Boot service from the backend API. It communicates with the backend exclusively via RabbitMQ:

- **Inbound (gateway → backend):** TelemetryMessage, LocationMessage, DeviceEventMessage, AlertEventMessage, CommandResultMessage
- **Outbound (backend → gateway):** CommandMessage

## Reused Protocol Code

The TCP protocol parsing logic (ConcoxV5, JT808) is copied directly from the HarvestTracker project per `docs/YANTRAGO_PROJECT_STRUCTURE.md` section 11. Only package declarations were changed — no protocol logic was rewritten.

## Building

```powershell
.\gradlew.bat :device-gateway:build -x test
```

## Running

```powershell
java -jar device-gateway/build/libs/device-gateway-1.0.0.jar --spring.profiles.active=dev
```

## Configuration

See `src/main/resources/application.yml` for all configurable parameters. Secrets must come from environment variables, never committed to source.
