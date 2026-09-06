package com.yantrago.api.service;

import com.yantrago.api.dto.device.CreateDeviceRequest;
import com.yantrago.api.dto.device.DeviceDto;
import com.yantrago.api.dto.device.UpdateDeviceRequest;
import com.yantrago.api.model.Device;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Device CRUD service.
 * Devices are physical tracker devices (IMEI, SIM, protocol) bound to machines.
 * All queries filter by organization_id from OwnerContextService.
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final MachineRepository machineRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public DeviceService(DeviceRepository deviceRepository,
                         MachineRepository machineRepository,
                         OwnerContextService ownerContextService,
                         TenantGuard tenantGuard) {
        this.deviceRepository = deviceRepository;
        this.machineRepository = machineRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public Page<DeviceDto> listDevices(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return deviceRepository.findByOrganizationId(orgId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public DeviceDto getDevice(UUID id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        tenantGuard.validateTenantAccess(device.getOrganizationId());
        return toDto(device);
    }

    @Transactional(readOnly = true)
    public DeviceDto getDeviceByImei(String imei) {
        UUID orgId = ownerContextService.getOrganizationId();
        Device device = deviceRepository.findByOrganizationIdAndImei(orgId, imei)
                .orElseThrow(() -> new IllegalArgumentException("Device not found with IMEI: " + imei));
        return toDto(device);
    }

    @Transactional
    public DeviceDto createDevice(CreateDeviceRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Validate IMEI uniqueness (global)
        if (deviceRepository.findByImei(request.getImei()).isPresent()) {
            throw new IllegalArgumentException("Device with this IMEI already exists");
        }

        // Validate machine belongs to same tenant
        if (request.getMachineId() != null) {
            machineRepository.findById(request.getMachineId())
                    .ifPresentOrElse(
                            m -> tenantGuard.validateTenantAccess(m.getOrganizationId()),
                            () -> { throw new IllegalArgumentException("Machine not found: " + request.getMachineId()); }
                    );
        }

        Device device = new Device();
        device.setOrganizationId(orgId);
        device.setMachineId(request.getMachineId());
        device.setImei(request.getImei());
        device.setSimNumber(request.getSimNumber());
        device.setProtocolType(request.getProtocolType());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setIsActive(true);

        device = deviceRepository.save(device);
        log.info("Created device id={} imei={} orgId={}", device.getId(), device.getImei(), orgId);
        return toDto(device);
    }

    @Transactional
    public DeviceDto updateDevice(UUID id, UpdateDeviceRequest request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        tenantGuard.validateTenantAccess(device.getOrganizationId());

        if (request.getMachineId() != null) {
            machineRepository.findById(request.getMachineId())
                    .ifPresentOrElse(
                            m -> tenantGuard.validateTenantAccess(m.getOrganizationId()),
                            () -> { throw new IllegalArgumentException("Machine not found: " + request.getMachineId()); }
                    );
            device.setMachineId(request.getMachineId());
        }
        if (request.getSimNumber() != null) device.setSimNumber(request.getSimNumber());
        if (request.getFirmwareVersion() != null) device.setFirmwareVersion(request.getFirmwareVersion());
        if (request.getIsActive() != null) device.setIsActive(request.getIsActive());

        device = deviceRepository.save(device);
        log.info("Updated device id={}", device.getId());
        return toDto(device);
    }

    @Transactional
    public void deleteDevice(UUID id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        tenantGuard.validateTenantAccess(device.getOrganizationId());
        deviceRepository.delete(device);
        log.info("Deleted device id={}", id);
    }

    private DeviceDto toDto(Device d) {
        return new DeviceDto(
                d.getId(),
                d.getOrganizationId(),
                d.getMachineId(),
                d.getImei(),
                d.getSimNumber(),
                d.getProtocolType(),
                d.getFirmwareVersion(),
                d.getIsActive(),
                d.getLastSeenAt(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
