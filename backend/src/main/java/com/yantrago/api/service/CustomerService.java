package com.yantrago.api.service;

import com.yantrago.api.dto.customer.CreateCustomerRequest;
import com.yantrago.api.dto.customer.CustomerDto;
import com.yantrago.api.dto.customer.UpdateCustomerRequest;
import com.yantrago.api.model.Customer;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Customer CRUD service.
 * All queries filter by organization_id from OwnerContextService.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public CustomerService(CustomerRepository customerRepository,
                            OwnerContextService ownerContextService,
                            TenantGuard tenantGuard) {
        this.customerRepository = customerRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> listCustomers(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return customerRepository.findByOrganizationId(orgId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());
        return toDto(customer);
    }

    @Transactional
    public CustomerDto createCustomer(CreateCustomerRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();

        Customer customer = new Customer();
        customer.setOrganizationId(orgId);
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setLatitude(request.getLatitude());
        customer.setLongitude(request.getLongitude());
        customer.setIsActive(true);

        customer = customerRepository.save(customer);
        log.info("Created customer id={} orgId={}", customer.getId(), orgId);
        return toDto(customer);
    }

    @Transactional
    public CustomerDto updateCustomer(UUID id, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());

        if (request.getName() != null) customer.setName(request.getName());
        if (request.getEmail() != null) customer.setEmail(request.getEmail());
        if (request.getPhone() != null) customer.setPhone(request.getPhone());
        if (request.getAddress() != null) customer.setAddress(request.getAddress());
        if (request.getLatitude() != null) customer.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) customer.setLongitude(request.getLongitude());
        if (request.getIsActive() != null) customer.setIsActive(request.getIsActive());

        customer = customerRepository.save(customer);
        log.info("Updated customer id={}", customer.getId());
        return toDto(customer);
    }

    @Transactional
    public void deleteCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());
        customerRepository.delete(customer);
        log.info("Deleted customer id={}", id);
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(
                c.getId(),
                c.getOrganizationId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getLatitude(),
                c.getLongitude(),
                c.getIsActive()
        );
    }
}
