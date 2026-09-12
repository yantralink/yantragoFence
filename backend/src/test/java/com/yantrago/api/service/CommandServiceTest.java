package com.yantrago.api.service;

import com.yantrago.api.dto.command.CommandRequest;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.dto.command.CommandStatusDto;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineCommand;
import com.yantrago.api.queue.CommandProducer;
import com.yantrago.api.repository.CommandRepository;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandService.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * Per AGENTS.md rule 6: all commands must be auditable via machine_commands + command_attempts.
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 */
class CommandServiceTest {

    private CommandRepository commandRepository;
    private MachineRepository machineRepository;
    private DeviceRepository deviceRepository;
    private CommandStateMachine stateMachine;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private PermissionEvaluator permissionEvaluator;
    private CommandProducer commandProducer;
    private CommandService commandService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID commandId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        commandRepository = mock(CommandRepository.class);
        machineRepository = mock(MachineRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        stateMachine = new CommandStateMachine();
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        commandProducer = mock(CommandProducer.class);

        commandService = new CommandService(
                commandRepository, machineRepository, deviceRepository,
                stateMachine, ownerContextService, tenantGuard,
                permissionEvaluator, commandProducer
        );
    }

    @Test
    @DisplayName("createCommand should create command in PENDING state and publish to RabbitMQ")
    void createCommand_shouldCreatePendingAndPublish() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);

        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(orgId);
        machine.setSerialNumber("IMEI123456");
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));
        com.yantrago.api.model.Device device = new com.yantrago.api.model.Device();
        device.setId(UUID.randomUUID());
        device.setOrganizationId(orgId);
        device.setImei("IMEI123456");
        device.setMachineId(machineId);
        when(deviceRepository.findByMachineId(machineId))
                .thenReturn(Optional.of(device));

        MachineCommand savedCommand = new MachineCommand();
        savedCommand.setId(commandId);
        savedCommand.setOrganizationId(orgId);
        savedCommand.setMachineId(machineId);
        savedCommand.setCommandType("ON");
        savedCommand.setStatus("PENDING");
        savedCommand.setAttemptCount(0);
        savedCommand.setMaxAttempts(3);
        savedCommand.setCreatedAt(LocalDateTime.now());
        when(commandRepository.save(any(MachineCommand.class))).thenReturn(savedCommand);

        CommandRequest request = new CommandRequest();
        request.setMachineId(machineId);
        request.setCommandType("ON");

        CommandResponse response = commandService.createCommand(request);

        assertEquals(commandId, response.getId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("ON", response.getCommandType());
        assertEquals(0, response.getAttemptCount());
        assertEquals(3, response.getMaxAttempts());

        // Verify command was published to RabbitMQ
        verify(commandProducer).sendCommand(commandId, machineId, "IMEI123456", "ON");
    }

    @Test
    @DisplayName("createCommand should throw when machine not found")
    void createCommand_shouldThrowWhenMachineNotFound() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.empty());

        CommandRequest request = new CommandRequest();
        request.setMachineId(machineId);
        request.setCommandType("ON");

        assertThrows(IllegalArgumentException.class, () -> commandService.createCommand(request));
        verify(commandProducer, never()).sendCommand(any(), any(), any(), any());
    }

    @Test
    @DisplayName("createCommand should validate tenant access on machine")
    void createCommand_shouldValidateTenantAccess() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);

        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(orgId);
        machine.setSerialNumber("IMEI123");
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));
        com.yantrago.api.model.Device device2 = new com.yantrago.api.model.Device();
        device2.setId(UUID.randomUUID());
        device2.setOrganizationId(orgId);
        device2.setImei("IMEI123");
        device2.setMachineId(machineId);
        when(deviceRepository.findByMachineId(machineId))
                .thenReturn(Optional.of(device2));

        MachineCommand saved = new MachineCommand();
        saved.setId(commandId);
        saved.setOrganizationId(orgId);
        saved.setMachineId(machineId);
        saved.setCommandType("ON");
        saved.setStatus("PENDING");
        saved.setAttemptCount(0);
        saved.setMaxAttempts(3);
        saved.setCreatedAt(LocalDateTime.now());
        when(commandRepository.save(any())).thenReturn(saved);

        CommandRequest request = new CommandRequest();
        request.setMachineId(machineId);
        request.setCommandType("ON");

        commandService.createCommand(request);

        // Verify tenant guard was called with the machine's orgId
        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("getCommand should throw when command not found")
    void getCommand_shouldThrowWhenNotFound() {
        when(commandRepository.findById(commandId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> commandService.getCommand(commandId));
    }

    @Test
    @DisplayName("getCommand should validate tenant access")
    void getCommand_shouldValidateTenantAccess() {
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        command.setOrganizationId(orgId);
        command.setMachineId(machineId);
        command.setCommandType("ON");
        command.setStatus("PENDING");
        command.setAttemptCount(0);
        command.setMaxAttempts(3);
        command.setCreatedAt(LocalDateTime.now());
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));

        commandService.getCommand(commandId);

        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("getCommandStatus should return lightweight status")
    void getCommandStatus_shouldReturnStatus() {
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        command.setOrganizationId(orgId);
        command.setMachineId(machineId);
        command.setStatus("SENT");
        command.setAttemptCount(1);
        command.setMaxAttempts(3);
        command.setCreatedAt(LocalDateTime.now());
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));

        CommandStatusDto status = commandService.getCommandStatus(commandId);

        assertEquals(commandId, status.getId());
        assertEquals("SENT", status.getStatus());
        assertEquals(1, status.getAttemptCount());
        assertEquals(3, status.getMaxAttempts());
    }

    @Test
    @DisplayName("transitionCommand should validate state machine transitions")
    void transitionCommand_shouldValidateTransitions() {
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        command.setOrganizationId(orgId);
        command.setMachineId(machineId);
        command.setStatus("PENDING");
        command.setCreatedAt(LocalDateTime.now());
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommandResponse response = commandService.transitionCommand(commandId, "QUEUED", null);

        assertEquals("QUEUED", response.getStatus());
    }

    @Test
    @DisplayName("transitionCommand should throw for invalid transition")
    void transitionCommand_shouldThrowForInvalidTransition() {
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        command.setOrganizationId(orgId);
        command.setStatus("DONE");
        command.setCreatedAt(LocalDateTime.now());
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));

        assertThrows(IllegalStateException.class,
                () -> commandService.transitionCommand(commandId, "PENDING", null));
    }

    @Test
    @DisplayName("transitionCommand should set completedAt for terminal states")
    void transitionCommand_shouldSetCompletedAtForTerminal() {
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        command.setOrganizationId(orgId);
        command.setStatus("ACK");
        command.setCreatedAt(LocalDateTime.now());
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommandResponse response = commandService.transitionCommand(commandId, "DONE", null);

        assertNotNull(response.getCompletedAt());
    }

    @Test
    @DisplayName("recordAttempt should increment attempt count")
    void recordAttempt_shouldIncrementCount() {
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        command.setOrganizationId(orgId);
        command.setAttemptCount(1);
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(command));
        when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        commandService.recordAttempt(commandId, 2, "SENT", null);

        ArgumentCaptor<MachineCommand> captor = ArgumentCaptor.forClass(MachineCommand.class);
        verify(commandRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getAttemptCount());
    }
}
