package com.yantrago.api.service;

import com.yantrago.api.dto.organization.CreateOrganizationRequest;
import com.yantrago.api.dto.organization.OrganizationDto;
import com.yantrago.api.dto.organization.UpdateOrganizationRequest;
import com.yantrago.api.model.Organization;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Organization CRUD service.
 * Organizations are the root of multi-tenancy.
 * Super_admin can create/list/update/delete/activate/deactivate organizations.
 * Tenant users can only view their own organization.
 */
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OwnerContextService ownerContextService;
    private final JdbcTemplate jdbcTemplate;

    public OrganizationService(OrganizationRepository organizationRepository,
                               UserRepository userRepository,
                               OwnerContextService ownerContextService,
                               JdbcTemplate jdbcTemplate) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.ownerContextService = ownerContextService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationDto> listOrganizations(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        if (orgId == null) {
            return organizationRepository.findAll(pageable).map(this::toDto);
        }
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Current organization not found: " + orgId));
        return new PageImpl<>(java.util.List.of(toDto(org)), pageable, 1);
    }

    @Transactional(readOnly = true)
    public OrganizationDto getOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));
        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg != null && !currentOrg.equals(id)) {
            throw new SecurityException("Cross-tenant access denied: cannot view another organization");
        }
        return toDto(org);
    }

    @Transactional
    public OrganizationDto createOrganization(CreateOrganizationRequest request) {
        // Auto-generate slug from name
        String slug = generateSlug(request.getName());

        Organization org = new Organization();
        org.setName(request.getName());
        org.setSlug(slug);
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

        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg != null && !currentOrg.equals(id)) {
            throw new SecurityException("Cross-tenant access denied: cannot update another organization");
        }

        if (request.getName() != null) org.setName(request.getName());
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
        organizationRepository.delete(org);
        log.info("Deleted organization id={}", id);
    }

    @Transactional
    public OrganizationDto activateOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));
        org.setIsActive(true);
        org = organizationRepository.save(org);
        log.info("Activated organization id={}", id);
        return toDto(org);
    }

    @Transactional
    public OrganizationDto deactivateOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));
        org.setIsActive(false);
        org = organizationRepository.save(org);

        // Deactivate all users in this org (block login)
        jdbcTemplate.update("UPDATE users SET is_active = false WHERE organization_id = ?", id);

        log.info("Deactivated organization id={} and all its users", id);
        return toDto(org);
    }

    /**
     * Auto-generate a URL-friendly slug from the organization name.
     * If the slug already exists, append a number suffix.
     */
    private String generateSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (baseSlug.isEmpty()) {
            baseSlug = "organization";
        }

        String slug = baseSlug;
        int suffix = 2;
        while (organizationRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }
        return slug;
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
