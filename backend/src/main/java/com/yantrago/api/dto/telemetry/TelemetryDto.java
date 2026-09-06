package com.yantrago.api.dto.telemetry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated telemetry response for a machine.
 */
public class TelemetryDto {

    private String machineId;
    private List<VoltageDto> voltageReadings;
    private List<BatteryDto> batteryReadings;
    private List<GsmDto> gsmReadings;
    private LocalDateTime from;
    private LocalDateTime to;

    public TelemetryDto() {}

    public TelemetryDto(String machineId, List<VoltageDto> voltageReadings, List<BatteryDto> batteryReadings,
                        List<GsmDto> gsmReadings, LocalDateTime from, LocalDateTime to) {
        this.machineId = machineId;
        this.voltageReadings = voltageReadings;
        this.batteryReadings = batteryReadings;
        this.gsmReadings = gsmReadings;
        this.from = from;
        this.to = to;
    }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    public List<VoltageDto> getVoltageReadings() { return voltageReadings; }
    public void setVoltageReadings(List<VoltageDto> voltageReadings) { this.voltageReadings = voltageReadings; }
    public List<BatteryDto> getBatteryReadings() { return batteryReadings; }
    public void setBatteryReadings(List<BatteryDto> batteryReadings) { this.batteryReadings = batteryReadings; }
    public List<GsmDto> getGsmReadings() { return gsmReadings; }
    public void setGsmReadings(List<GsmDto> gsmReadings) { this.gsmReadings = gsmReadings; }
    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }
    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }
}
