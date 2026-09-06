package com.yantrago.api.controller;

import com.yantrago.api.model.Role;
import com.yantrago.api.repository.RoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Role endpoints — list available roles for user creation.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }
}
