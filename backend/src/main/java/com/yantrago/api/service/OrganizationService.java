package com.yantrago.api.service;

import com.yantrago.api.dto.organization.CreateOrganizationRequest;
import com.yantrago.api.dto.organization.OrganizationDto;
import com.yantrago.api.dto.organization.UpdateOrganizationRequest;
import com.yantrago.api.model.Organization;
import com.yantrago.api.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Organization CRUD service.
 * Organizations are the root of multi-tenancy.
 * Super_admin can create/list/update/delete organizations.
 * Tenant users can only view their own organization.
 */
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final OwnerContextService ownerContextService;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OwnerContextService ownerContextService) {
        this.organizationRepository = organizationRepository;
        this.ownerContextService = ownerContextService;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationDto> listOrganizations(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        if (orgId == null) {
            // Super_admin — list all
            return organizationRepository.findAll(pageable).map(this::toDto);
        }
        // Tenant user — only their own org
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Current organization not found: " + orgId));
        return new PageImpl<>(java.util.List.of(toDto(org)), pageable, 1);
    }

    @Transactional(readOnly = true)
    public OrganizationDto getOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));
        // Tenant users can only view their own org
        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg != null && !currentOrg.equals(id)) {
            throw new SecurityException("Cross-tenant access denied: cannot view another organization");
        }
        return toDto(org);
    }

    @Transactional
    public OrganizationDto createOrganization(CreateOrganizationRequest request) {
        // Only super_admin should create organizations (enforced by RBAC at controller level)
        if (organizationRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Organization with this slug already exists");
        }

        Organization org = new Organization();
        org.setName(request.getName());
        org.setSlug(request.getSlug());
        org.setWhiteLabelConfig(request.getWhiteLabelConfig());
        org.setIsActive(true);

        org = organizationRepository.save(org);
        log.info("Created organization id={} slug={}", org.getId(), org.getSlug());
        return toDto(org);
    }

    @Transactional
    public OrganizationDto updateOrganization(UUID id, UpdateOrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        // Tenant users can only update their own org
        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg != null && !currentOrg.equals(id)) {
            throw new SecurityException("Cross-tenant access denied: cannot update another organization");
        }

        if (request.getName() != null) org.setName(request.getName());
        if (request.getSlug() != null) org.setSlug(request.getSlug());
        if (request.getWhiteLabelConfig() != null) org.setWhiteLabelConfig(request.getWhiteLabelConfig());
        if (request.getIsActive() != null) org.setIsActive(request.getIsActive());

        org = organizationRepository.save(org);
        log.info("Updated organization id={}", org.getId());
        return toDto(org);
    }

    @Transactional
    public void deleteOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));
        // Only super_admin should delete (enforced by RBAC)
        organizationRepository.delete(org);
        log.info("Deleted organization id={}", id);
    }

    private OrganizationDto toDto(Organization org) {
        return new OrganizationDto(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getWhiteLabelConfig(),
                org.getIsActive()
        );
    }
}
