package com.yantrago.api.controller;

import com.yantrago.api.dto.command.CommandRequest;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.dto.command.CommandStatusDto;
import com.yantrago.api.service.CommandService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Command lifecycle endpoints.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * Per AGENTS.md rule 6: all commands must be auditable.
 *
 * POST /api/v1/commands — issue a new command (ON/OFF)
 * GET  /api/v1/commands — list commands (paged, filtered by tenant)
 * GET  /api/v1/commands/{id} — get command details
 * GET  /api/v1/commands/{id}/status — get lightweight status
 */
@RestController
@RequestMapping("/api/v1/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('command:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CommandResponse> createCommand(@Valid @RequestBody CommandRequest request) {
        return ResponseEntity.ok(commandService.createCommand(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('command:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<CommandResponse>> listCommands(
            @RequestParam(required = false) UUID machineId,
            Pageable pageable) {
        if (machineId != null) {
            return ResponseEntity.ok(commandService.listCommandsByMachine(machineId, pageable));
        }
        return ResponseEntity.ok(commandService.listCommands(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('command:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CommandResponse> getCommand(@PathVariable UUID id) {
        return ResponseEntity.ok(commandService.getCommand(id));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('command:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CommandStatusDto> getCommandStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(commandService.getCommandStatus(id));
    }
}
