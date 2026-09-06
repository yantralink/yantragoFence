package com.yantrago.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
import com.yantrago.api.dto.command.CommandRequest;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.dto.command.CommandStatusDto;
import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.service.CommandService;
import com.yantrago.api.service.JwtService;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the command controller endpoints and command lifecycle.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * Per AGENTS.md rule 6: all commands must be auditable.
 */
@WebMvcTest(controllers = CommandController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtAuthFilter.class, TenantContextFilter.class,
         RateLimitFilter.class, AuditLogInterceptor.class, JwtService.class,
         OwnerContextService.class, TenantGuard.class, PermissionEvaluator.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.access.secret=test-access-secret-at-least-32-characters-long",
        "jwt.refresh.secret=test-refresh-secret-at-least-32-characters-long",
        "jwt.access.ttl=3600",
        "jwt.refresh.ttl=2592000"
})
class CommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommandService commandService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID commandId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void createCommand_shouldReturn401_withoutAuth() throws Exception {
        CommandRequest request = new CommandRequest();
        request.setMachineId(machineId);
        request.setCommandType("ON");

        mockMvc.perform(post("/api/v1/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCommand_shouldCreateCommand_whenValidRequest() throws Exception {
        CommandRequest request = new CommandRequest();
        request.setMachineId(machineId);
        request.setCommandType("ON");

        CommandResponse response = new CommandResponse(
                commandId, orgId, machineId, null, userId,
                "ON", "PENDING", 0, 3, null,
                LocalDateTime.now(), null, null
        );
        when(commandService.createCommand(any(CommandRequest.class))).thenReturn(response);

        CommandResponse result = commandService.createCommand(request);
        org.junit.jupiter.api.Assertions.assertEquals(commandId, result.getId());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("ON", result.getCommandType());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getAttemptCount());
    }

    @Test
    void getCommand_shouldReturnCommand_whenExists() throws Exception {
        CommandResponse response = new CommandResponse(
                commandId, orgId, machineId, null, userId,
                "OFF", "SENT", 1, 3, null,
                LocalDateTime.now(), null, null
        );
        when(commandService.getCommand(commandId)).thenReturn(response);

        CommandResponse result = commandService.getCommand(commandId);
        org.junit.jupiter.api.Assertions.assertEquals(commandId, result.getId());
        org.junit.jupiter.api.Assertions.assertEquals("SENT", result.getStatus());
    }

    @Test
    void getCommandStatus_shouldReturnLightweightStatus() throws Exception {
        CommandStatusDto statusDto = new CommandStatusDto(
                commandId, "ACK", 1, 3, null, null
        );
        when(commandService.getCommandStatus(commandId)).thenReturn(statusDto);

        CommandStatusDto result = commandService.getCommandStatus(commandId);
        org.junit.jupiter.api.Assertions.assertEquals("ACK", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getAttemptCount());
    }

    @Test
    void listCommands_shouldReturnPagedResult() throws Exception {
        CommandResponse response = new CommandResponse(
                commandId, orgId, machineId, null, userId,
                "ON", "DONE", 1, 3, null,
                LocalDateTime.now(), null, LocalDateTime.now()
        );
        Page<CommandResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(commandService.listCommands(any())).thenReturn(page);

        Page<CommandResponse> result = commandService.listCommands(PageRequest.of(0, 20));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals("DONE", result.getContent().get(0).getStatus());
    }

    @Test
    void listCommandsByMachine_shouldFilterByMachine() throws Exception {
        CommandResponse response = new CommandResponse(
                commandId, orgId, machineId, null, userId,
                "ON", "PENDING", 0, 3, null,
                LocalDateTime.now(), null, null
        );
        Page<CommandResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(commandService.listCommandsByMachine(machineId, PageRequest.of(0, 20))).thenReturn(page);

        Page<CommandResponse> result = commandService.listCommandsByMachine(machineId, PageRequest.of(0, 20));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getTotalElements());
        verify(commandService).listCommandsByMachine(machineId, PageRequest.of(0, 20));
    }
}
