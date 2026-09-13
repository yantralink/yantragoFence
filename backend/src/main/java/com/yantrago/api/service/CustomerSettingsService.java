package com.yantrago.api.service;

import com.yantrago.api.dto.theft.CustomerSettingsDto;
import com.yantrago.api.dto.theft.CustomerSettingsRequest;
import com.yantrago.api.model.Customer;
import com.yantrago.api.model.CustomerSettings;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.CustomerSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Customer settings service — manages per-customer theft protection defaults.
 *
 * Customers can set their preferred geofence radius and speed threshold.
 * These defaults are used when the customer enables theft protection for
 * a new machine, so they don't have to configure each machine individually.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 */
@Service
public class CustomerSettingsService {

    private static final Logger log = LoggerFactory.getLogger(CustomerSettingsService.class);

    private final CustomerSettingsRepository customerSettingsRepository;
    private final CustomerRepository customerRepository;

    public CustomerSettingsService(CustomerSettingsRepository customerSettingsRepository,
                                     CustomerRepository customerRepository) {
        this.customerSettingsRepository = customerSettingsRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public CustomerSettingsDto getSettings() {
        UUID customerId = requireCustomerId();
        CustomerSettings settings = customerSettingsRepository
                .findByCustomerId(customerId)
                .orElseGet(() -> createDefaultSettings(customerId));
        return toDto(settings);
    }

    @Transactional
    public CustomerSettingsDto updateSettings(CustomerSettingsRequest request) {
        UUID customerId = requireCustomerId();
        CustomerSettings settings = customerSettingsRepository
                .findByCustomerId(customerId)
                .orElseGet(() -> createDefaultSettings(customerId));

        settings.setDefaultGeofenceRadiusMeters(request.getDefaultGeofenceRadiusMeters());
        settings.setDefaultSpeedThresholdKmh(request.getDefaultSpeedThresholdKmh());
        settings = customerSettingsRepository.save(settings);

        log.info("Updated customer settings for customer={} radius={}m speed={}km/h",
                customerId, request.getDefaultGeofenceRadiusMeters(),
                request.getDefaultSpeedThresholdKmh());
        return toDto(settings);
    }

    /**
     * Returns the customer's defaults, or system defaults if not yet configured.
     * Used by TheftProtectionService when enabling protection.
     */
    @Transactional(readOnly = true)
    public CustomerSettings getOrCreateDefaults(UUID customerId) {
        return customerSettingsRepository
                .findByCustomerId(customerId)
                .orElseGet(() -> createDefaultSettings(customerId));
    }

    private CustomerSettings createDefaultSettings(UUID customerId) {
        CustomerSettings settings = new CustomerSettings();
        settings.setCustomerId(customerId);
        settings.setDefaultGeofenceRadiusMeters(200);
        settings.setDefaultSpeedThresholdKmh(10);
        return customerSettingsRepository.save(settings);
    }

    /**
     * Resolves the current customer ID from the JWT. Only works for
     * customer-role users. Admins get a SecurityException.
     */
    private UUID requireCustomerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new SecurityException("Authentication required");
        }
        boolean isCustomer = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_CUSTOMER".equals(a));
        if (!isCustomer) {
            throw new SecurityException(
                    "Customer settings are only available for customer-role users");
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UUID userId)) {
            throw new SecurityException("Invalid authentication principal");
        }
        return customerRepository.findByUserId(userId)
                .map(Customer::getId)
                .orElseThrow(() -> new SecurityException("Customer record not found for current user"));
    }

    private CustomerSettingsDto toDto(CustomerSettings s) {
        return new CustomerSettingsDto(
                s.getId(),
                s.getCustomerId(),
                s.getDefaultGeofenceRadiusMeters(),
                s.getDefaultSpeedThresholdKmh(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
