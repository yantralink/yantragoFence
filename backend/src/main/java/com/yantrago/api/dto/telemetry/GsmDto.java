package com.yantrago.api.dto.telemetry;

import java.time.LocalDateTime;

/**
 * GSM signal strength reading DTO.
 */
public class GsmDto {

    private LocalDateTime recordedAt;
    private Integer gsmSignal;

    public GsmDto() {}

    public GsmDto(LocalDateTime recordedAt, Integer gsmSignal) {
        this.recordedAt = recordedAt;
        this.gsmSignal = gsmSignal;
    }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public Integer getGsmSignal() { return gsmSignal; }
    public void setGsmSignal(Integer gsmSignal) { this.gsmSignal = gsmSignal; }
}
