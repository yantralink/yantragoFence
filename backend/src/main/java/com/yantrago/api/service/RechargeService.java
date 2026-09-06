package com.yantrago.api.service;

import com.yantrago.api.model.Recharge;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.RechargeRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Recharge service — tracks SIM/device recharges.
 * All queries filter by organization_id from OwnerContextService.
 */
@Service
public class RechargeService {

    private static final Logger log = LoggerFactory.getLogger(RechargeService.class);

    private final RechargeRepository rechargeRepository;
    private final DeviceRepository deviceRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public RechargeService(RechargeRepository rechargeRepository,
                           DeviceRepository deviceRepository,
                           OwnerContextService ownerContextService,
                           TenantGuard tenantGuard) {
        this.rechargeRepository = rechargeRepository;
        this.deviceRepository = deviceRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public Page<Recharge> listRecharges(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return rechargeRepository.findByOrganizationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Recharge> listRechargesByDevice(UUID deviceId, Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return rechargeRepository.findByOrganizationIdAndDeviceId(orgId, deviceId, pageable);
    }

    @Transactional(readOnly = true)
    public Recharge getRecharge(UUID id) {
        Recharge recharge = rechargeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recharge not found: " + id));
        tenantGuard.validateTenantAccess(recharge.getOrganizationId());
        return recharge;
    }

    @Transactional
    public Recharge createRecharge(UUID deviceId, BigDecimal amount, String currency,
                                    String provider, String planName, LocalDateTime validUntil) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Validate device belongs to tenant
        deviceRepository.findById(deviceId).ifPresentOrElse(
                d -> tenantGuard.validateTenantAccess(d.getOrganizationId()),
                () -> { throw new IllegalArgumentException("Device not found: " + deviceId); }
        );

        Recharge recharge = new Recharge();
        recharge.setOrganizationId(orgId);
        recharge.setDeviceId(deviceId);
        recharge.setAmount(amount);
        recharge.setCurrency(currency != null ? currency : "INR");
        recharge.setProvider(provider);
        recharge.setPlanName(planName);
        recharge.setRechargedAt(LocalDateTime.now());
        recharge.setValidUntil(validUntil);

        recharge = rechargeRepository.save(recharge);
        log.info("Created recharge id={} deviceId={} amount={} {}", recharge.getId(), deviceId, amount, recharge.getCurrency());
        return recharge;
    }
}
