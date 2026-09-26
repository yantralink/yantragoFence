package com.yantrago.api.controller;

import com.yantrago.api.dto.device.CreateDeviceRequest;
import com.yantrago.api.dto.device.DeviceDto;
import com.yantrago.api.dto.device.UpdateDeviceRequest;
import com.yantrago.api.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Device CRUD endpoints.
 * Supports lookup by IMEI: GET /api/v1/devices/imei/{imei}
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('device:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<DeviceDto>> listDevices(Pageable pageable) {
        return ResponseEntity.ok(deviceService.listDevices(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<DeviceDto> getDevice(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.getDevice(id));
    }

    @GetMapping("/imei/{imei}")
    @PreAuthorize("hasAuthority('device:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<DeviceDto> getDeviceByImei(@PathVariable String imei) {
        return ResponseEntity.ok(deviceService.getDeviceByImei(imei));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('device:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<DeviceDto> createDevice(@Valid @RequestBody CreateDeviceRequest request) {
        return ResponseEntity.ok(deviceService.createDevice(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('device:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<DeviceDto> updateDevice(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateDeviceRequest request) {
        return ResponseEntity.ok(deviceService.updateDevice(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }
}
