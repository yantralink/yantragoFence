package com.yantrago.gateway.service;

import com.yantrago.gateway.model.GpsIngestRequest;
import com.yantrago.gateway.queue.DeviceEventProducer;
import com.yantrago.gateway.queue.TelemetryProducer;
import com.yantrago.shared.queue.LocationMessage;
import com.yantrago.shared.queue.TelemetryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelemetryForwardService.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 17: use shared message contracts.
 */
class TelemetryForwardServiceTest {

    private TelemetryProducer telemetryProducer;
    private DeviceEventProducer deviceEventProducer;
    private DeviceMappingCacheService deviceMappingCacheService;
    private TelemetryForwardService telemetryForwardService;

    private final UUID deviceId = UUID.randomUUID();
    private final String imei = "123456789012345";

    @BeforeEach
    void setUp() {
        telemetryProducer = mock(TelemetryProducer.class);
        deviceEventProducer = mock(DeviceEventProducer.class);
        deviceMappingCacheService = mock(DeviceMappingCacheService.class);
        telemetryForwardService = new TelemetryForwardService(
                telemetryProducer, deviceEventProducer, deviceMappingCacheService
        );
    }

    @Test
    @DisplayName("ingest should skip when request is null")
    void ingest_shouldSkipWhenRequestIsNull() {
        telemetryForwardService.ingest(null);
        verify(telemetryProducer, never()).publishTelemetry(any());
        verify(deviceEventProducer, never()).publishLocation(any());
    }

    @Test
    @DisplayName("ingest should skip when deviceId is null")
    void ingest_shouldSkipWhenDeviceIdIsNull() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(null);
        telemetryForwardService.ingest(request);
        verify(telemetryProducer, never()).publishTelemetry(any());
    }

    @Test
    @DisplayName("ingest should skip when deviceId is invalid UUID")
    void ingest_shouldSkipWhenDeviceIdIsInvalid() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId("not-a-uuid");
        telemetryForwardService.ingest(request);
        verify(telemetryProducer, never()).publishTelemetry(any());
    }

    @Test
    @DisplayName("ingest should publish telemetry message")
    void ingest_shouldPublishTelemetry() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(deviceId.toString());
        request.setGsmSignalStrength(25);
        request.setExternalPowerConnected(true);

        telemetryForwardService.ingest(request);

        verify(telemetryProducer).publishTelemetry(any(TelemetryMessage.class));
    }

    @Test
    @DisplayName("ingest should publish location when lat and lng are present")
    void ingest_shouldPublishLocationWhenCoordinatesPresent() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(deviceId.toString());
        request.setLat(12.9716);
        request.setLng(77.5946);
        request.setSpeedKph(45.0);
        request.setHeading(180.0);

        telemetryForwardService.ingest(request);

        verify(deviceEventProducer).publishLocation(any(LocationMessage.class));
    }

    @Test
    @DisplayName("ingest should NOT publish location when lat/lng are null")
    void ingest_shouldNotPublishLocationWhenCoordinatesNull() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(deviceId.toString());
        // No lat/lng set

        telemetryForwardService.ingest(request);

        verify(deviceEventProducer, never()).publishLocation(any());
    }

    @Test
    @DisplayName("ingest should use sentAt timestamp when provided")
    void ingest_shouldUseSentAtWhenProvided() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(deviceId.toString());
        long sentAt = System.currentTimeMillis();
        request.setSentAt(sentAt);

        telemetryForwardService.ingest(request);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryProducer).publishTelemetry(captor.capture());
        assertEquals(sentAt, captor.getValue().getTimestamp().toEpochMilli());
    }

    @Test
    @DisplayName("forwardTelemetry should publish telemetry message with all fields")
    void forwardTelemetry_shouldPublishWithAllFields() {
        telemetryForwardService.forwardTelemetry(deviceId, imei, 12.5, 85.0, 20);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryProducer).publishTelemetry(captor.capture());
        assertEquals(deviceId, captor.getValue().getDeviceId());
        assertEquals(imei, captor.getValue().getImei());
        assertEquals(12.5, captor.getValue().getVoltage());
        assertEquals(85.0, captor.getValue().getBattery());
        assertEquals(20, captor.getValue().getGsmSignal());
    }

    @Test
    @DisplayName("forwardLocation should publish location message with all fields")
    void forwardLocation_shouldPublishWithAllFields() {
        telemetryForwardService.forwardLocation(deviceId, imei, 12.9716, 77.5946, 45.0, 180.0);

        ArgumentCaptor<LocationMessage> captor = ArgumentCaptor.forClass(LocationMessage.class);
        verify(deviceEventProducer).publishLocation(captor.capture());
        assertEquals(deviceId, captor.getValue().getDeviceId());
        assertEquals(imei, captor.getValue().getImei());
        assertEquals(12.9716, captor.getValue().getLatitude());
        assertEquals(77.5946, captor.getValue().getLongitude());
        assertEquals(45.0, captor.getValue().getSpeed());
    }

    @Test
    @DisplayName("forwardTelemetry (interface method) should publish battery and GSM from heartbeat")
    void forwardTelemetry_interface_shouldPublishBatteryAndGsm() {
        telemetryForwardService.forwardTelemetry(deviceId, imei, 60.0, 3, true);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryProducer).publishTelemetry(captor.capture());
        assertEquals(deviceId, captor.getValue().getDeviceId());
        assertEquals(imei, captor.getValue().getImei());
        // BR05 does not provide voltage in volts — only a 7-level enum mapped to percentage
        assertNull(captor.getValue().getVoltage());
        assertEquals(60.0, captor.getValue().getBattery());
        assertEquals(3, captor.getValue().getGsmSignal());
    }

    @Test
    @DisplayName("forwardTelemetry (interface method) should skip when deviceId is null")
    void forwardTelemetry_interface_shouldSkipWhenDeviceIdIsNull() {
        telemetryForwardService.forwardTelemetry(null, imei, 60.0, 3, true);

        verify(telemetryProducer, never()).publishTelemetry(any());
    }

    @Test
    @DisplayName("ingest should use batteryLevel from request instead of faking 100.0")
    void ingest_shouldUseBatteryLevelFromRequest() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(deviceId.toString());
        request.setExternalPowerConnected(true);
        request.setBatteryLevel(60); // 60% from heartbeat voltage level byte

        telemetryForwardService.ingest(request);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryProducer).publishTelemetry(captor.capture());
        // Should use the real battery level (60.0), not a fake 100.0
        assertEquals(60.0, captor.getValue().getBattery());
    }

    @Test
    @DisplayName("ingest should send null battery when batteryLevel is not set (no heartbeat received)")
    void ingest_shouldSendNullBatteryWhenNotSet() {
        GpsIngestRequest request = new GpsIngestRequest();
        request.setDeviceId(deviceId.toString());
        request.setExternalPowerConnected(true);
        // batteryLevel not set — no heartbeat received yet

        telemetryForwardService.ingest(request);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryProducer).publishTelemetry(captor.capture());
        // Should be null, not a fake 100.0
        assertNull(captor.getValue().getBattery());
    }

    @Test
    @DisplayName("forwardVoltage should publish telemetry message with voltage only")
    void forwardVoltage_shouldPublishVoltageOnly() {
        telemetryForwardService.forwardVoltage(deviceId, imei, 12.22);

        ArgumentCaptor<TelemetryMessage> captor = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetryProducer).publishTelemetry(captor.capture());
        assertEquals(deviceId, captor.getValue().getDeviceId());
        assertEquals(imei, captor.getValue().getImei());
        assertEquals(12.22, captor.getValue().getVoltage());
        // Battery/GSM/charging should be null so backend COALESCE preserves existing values
        assertNull(captor.getValue().getBattery());
        assertNull(captor.getValue().getGsmSignal());
        assertNull(captor.getValue().getCharging());
    }

    @Test
    @DisplayName("forwardVoltage should skip when deviceId is null")
    void forwardVoltage_shouldSkipWhenDeviceIdIsNull() {
        telemetryForwardService.forwardVoltage(null, imei, 12.22);

        verify(telemetryProducer, never()).publishTelemetry(any());
    }
}
