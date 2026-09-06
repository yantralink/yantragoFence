package com.yantrago.api.service;

import com.yantrago.api.dto.customer.CreateCustomerRequest;
import com.yantrago.api.dto.customer.CustomerDto;
import com.yantrago.api.dto.customer.UpdateCustomerRequest;
import com.yantrago.api.model.Customer;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.Role;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.repository.RoleRepository;
import com.yantrago.api.repository.UserRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Customer CRUD service.
 *
 * Creating a customer also auto-creates a user account (role=customer)
 * so the customer can log in to the mobile app with their phone number.
 * Default password: "yantrago" (BCrypt hashed).
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final String DEFAULT_CUSTOMER_PASSWORD = "yantrago";

    private final CustomerRepository customerRepository;
    private final MachineRepository machineRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public CustomerService(CustomerRepository customerRepository,
                           MachineRepository machineRepository,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           OwnerContextService ownerContextService,
                           TenantGuard tenantGuard,
                           PasswordEncoder passwordEncoder,
                           JdbcTemplate jdbcTemplate) {
        this.customerRepository = customerRepository;
        this.machineRepository = machineRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> listCustomers(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        if (orgId == null) {
            // Super admin: list all
            return customerRepository.findAll(pageable).map(this::toDto);
        }
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

        // Check for duplicate phone in same org
        customerRepository.findByOrganizationIdAndPhone(orgId, request.getPhone())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Customer with this phone number already exists in this organization");
                });

        // Create user account for mobile app login (email = phone)
        User user = new User();
        user.setOrganizationId(orgId);
        user.setEmail(request.getPhone()); // phone is used as login ID
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_CUSTOMER_PASSWORD));
        user.setFullName(request.getName());
        user.setPhone(request.getPhone());
        user.setIsActive(true);
        user.setIsLocked(false);
        User savedUser = userRepository.saveAndFlush(user);

        // Assign 'customer' role
        roleRepository.findByName("customer").ifPresent(role -> {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    savedUser.getId(), role.getId());
        });

        // Create customer record linked to user
        Customer customer = new Customer();
        customer.setOrganizationId(orgId);
        customer.setUserId(savedUser.getId());
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setLatitude(request.getLatitude());
        customer.setLongitude(request.getLongitude());
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        // Assign machine if provided
        if (request.getAssignedMachineId() != null) {
            assignMachineToCustomer(request.getAssignedMachineId(), customer.getId());
        }

        log.info("Created customer id={} orgId={} userId={}", customer.getId(), orgId, user.getId());
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

        // Sync user active status
        if (request.getIsActive() != null && customer.getUserId() != null) {
            userRepository.findById(customer.getUserId()).ifPresent(u -> {
                u.setIsActive(request.getIsActive());
                userRepository.save(u);
            });
        }

        customer = customerRepository.save(customer);

        // Handle machine reassignment
        if (request.getAssignedMachineId() != null) {
            // Unassign any currently assigned machine first
            if (customer.getId() != null) {
                unassignCurrentMachine(customer.getId());
            }
            // Assign new machine (null means just unassign)
            if (!request.getAssignedMachineId().equals(new UUID(0, 0))) {
                assignMachineToCustomer(request.getAssignedMachineId(), customer.getId());
            }
        }

        log.info("Updated customer id={}", customer.getId());
        return toDto(customer);
    }

    @Transactional
    public void deleteCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());

        // Unassign any linked machines
        unassignCurrentMachine(customer.getId());

        // Deactivate (not delete) the linked user account
        if (customer.getUserId() != null) {
            userRepository.findById(customer.getUserId()).ifPresent(u -> {
                u.setIsActive(false);
                userRepository.save(u);
            });
        }

        customerRepository.delete(customer);
        log.info("Deleted customer id={}", id);
    }

    @Transactional
    public CustomerDto activateCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        // Activate linked user
        if (customer.getUserId() != null) {
            userRepository.findById(customer.getUserId()).ifPresent(u -> {
                u.setIsActive(true);
                userRepository.save(u);
            });
        }

        log.info("Activated customer id={}", id);
        return toDto(customer);
    }

    @Transactional
    public CustomerDto deactivateCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());
        customer.setIsActive(false);
        customer = customerRepository.save(customer);

        // Deactivate linked user
        if (customer.getUserId() != null) {
            userRepository.findById(customer.getUserId()).ifPresent(u -> {
                u.setIsActive(false);
                userRepository.save(u);
            });
        }

        log.info("Deactivated customer id={}", id);
        return toDto(customer);
    }

    @Transactional
    public void resetCustomerPassword(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());

        if (customer.getUserId() != null) {
            userRepository.findById(customer.getUserId()).ifPresent(u -> {
                u.setPasswordHash(passwordEncoder.encode(DEFAULT_CUSTOMER_PASSWORD));
                userRepository.save(u);
            });
        }

        log.info("Reset password for customer id={}", id);
    }

    private void assignMachineToCustomer(UUID machineId, UUID customerId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        machine.setCustomerId(customerId);
        machine.setStatus("ACTIVE");
        machineRepository.save(machine);
        log.info("Assigned machine {} to customer {}", machine.getMachineId(), customerId);
    }

    private void unassignCurrentMachine(UUID customerId) {
        // Find machines assigned to this customer and unassign
        machineRepository.findAll().stream()
                .filter(m -> customerId.equals(m.getCustomerId()))
                .forEach(m -> {
                    m.setCustomerId(null);
                    m.setStatus("IN_STOCK");
                    machineRepository.save(m);
                    log.info("Unassigned machine {} from customer {}", m.getMachineId(), customerId);
                });
    }

    private CustomerDto toDto(Customer c) {
        CustomerDto dto = new CustomerDto(
                c.getId(),
                c.getOrganizationId(),
                c.getUserId(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getLatitude(),
                c.getLongitude(),
                c.getIsActive()
        );
        // Find assigned machine
        machineRepository.findAll().stream()
                .filter(m -> c.getId().equals(m.getCustomerId()))
                .findFirst()
                .ifPresent(m -> {
                    dto.setAssignedMachineId(m.getId());
                    dto.setAssignedMachineName(m.getName());
                    dto.setAssignedMachineCode(m.getMachineId());
                });
        return dto;
    }
}
