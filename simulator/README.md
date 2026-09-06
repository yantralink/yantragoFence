# YantraGO Device Simulator

Java simulator for load testing the YantraGO TCP device gateway without real hardware. Simulates Concox V5 GPS trackers, JT808/T98 dashcams, and YantraGO fencing machines using the same protocol encoding as the gateway.

## Protocols Simulated

| Protocol | Port | Devices |
|----------|------|---------|
| Concox V5 / BR05 | 5000 | GPS trackers |
| JT808-2013 | 5001 | T98 dashcams |
| Fencing | 5002 | YantraGO fencing machines |

## Building

```powershell
.\gradlew.bat :simulator:build -x test
```

## Running

### Single device (manual testing)

```powershell
java -jar simulator/build/libs/simulator-1.0.0.jar
```

Default config: 1 device, 10s heartbeat, 30s GPS, targeting localhost.

### Hundred devices (integration testing)

```powershell
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.device-count=100
```

### Thousand devices (load testing)

```powershell
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.device-count=1000 --simulator.heartbeat-interval-ms=15000
```

### Ten thousand devices (stress testing)

```powershell
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.device-count=10000 --simulator.heartbeat-interval-ms=30000 --simulator.gps-interval-ms=60000
```

### Targeting a remote gateway

```powershell
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.target-host=192.168.1.100
```

### Selecting specific protocols

```powershell
# Only fencing machines
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.enabled-protocols=fencing

# Concox + JT808 (no fencing)
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.enabled-protocols=concox,jt808
```

## Configuration

All settings are in `src/main/resources/application.yml` under the `simulator` prefix:

| Property | Default | Description |
|----------|---------|-------------|
| `simulator.target-host` | localhost | Gateway host |
| `simulator.concox-port` | 5000 | Concox V5 TCP port |
| `simulator.jt808-port` | 5001 | JT808 TCP port |
| `simulator.fencing-port` | 5002 | Fencing TCP port |
| `simulator.device-count` | 1 | Number of devices to simulate per protocol |
| `simulator.heartbeat-interval-ms` | 10000 | Heartbeat interval |
| `simulator.gps-interval-ms` | 30000 | GPS location interval |
| `simulator.telemetry-interval-ms` | 60000 | Telemetry interval (fencing only) |
| `simulator.start-imei` | 867010070113452 | Starting IMEI (incremented per device) |
| `simulator.enabled-protocols` | concox,fencing | Comma-separated list of protocols to simulate |

## Scenarios

| Scenario | Devices | Heartbeat | GPS | Purpose |
|----------|---------|-----------|-----|---------|
| `single-device` | 1 | 10s | 30s | Manual testing |
| `hundred-devices` | 100 | 10s | 30s | Integration testing |
| `thousand-devices` | 1,000 | 15s | 60s | Load testing |
| `ten-thousand-devices` | 10,000 | 30s | 60s | Stress testing |

## Protocol Implementation

The simulator uses the same CRC-16/ITU, BCD encoding, and packet-building logic as the gateway handlers:

- **Concox V5**: `0x78 0x78` start bytes, BCD-encoded IMEI, CRC-16/ITU
- **JT808**: `0x7E` frame markers, XOR checksum, `0x7D` escape sequences
- **Fencing**: `0xAA 0x55` start bytes, `0x0D 0x0A` stop bytes, CRC-16/ITU

GPS coordinates are randomly generated around Bangalore (12.97°N, 77.59°E).
