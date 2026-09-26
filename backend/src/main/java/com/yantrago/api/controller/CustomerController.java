package com.yantrago.api.controller;

import com.yantrago.api.dto.customer.CreateCustomerRequest;
import com.yantrago.api.dto.customer.CustomerDto;
import com.yantrago.api.dto.customer.UpdateCustomerRequest;
import com.yantrago.api.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Customer CRUD + lifecycle endpoints.
 *
 * Creating a customer auto-creates a mobile app user account (role=customer).
 * Org admin can create/edit/delete/activate/deactivate customers and reset their password.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<CustomerDto>> listCustomers(Pageable pageable) {
        return ResponseEntity.ok(customerService.listCustomers(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerDto> getCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('customer:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerDto> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(customerService.createCustomer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerDto> updateCustomer(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:delete') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('customer:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerDto> activateCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.activateCustomer(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('customer:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerDto> deactivateCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.deactivateCustomer(id));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('customer:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> resetCustomerPassword(@PathVariable UUID id) {
        customerService.resetCustomerPassword(id);
        return ResponseEntity.noContent().build();
    }
}
