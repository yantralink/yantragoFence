package com.yantrago.api.service;

import com.yantrago.api.dto.command.CommandRequest;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.dto.command.CommandStatusDto;
import com.yantrago.api.model.CommandAttempt;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineCommand;
import com.yantrago.api.queue.CommandProducer;
import com.yantrago.api.repository.CommandRepository;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command lifecycle service.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * Per AGENTS.md rule 6: all commands must be auditable via machine_commands + command_attempts.
 *
 * Commands are created in PENDING state, transitioned through the state machine,
 * and every attempt is recorded in command_attempts.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final CommandRepository commandRepository;
    private final MachineRepository machineRepository;
    private final DeviceRepository deviceRepository;
    private final CommandStateMachine stateMachine;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final CommandProducer commandProducer;

    public CommandService(CommandRepository commandRepository,
                          MachineRepository machineRepository,
                          DeviceRepository deviceRepository,
                          CommandStateMachine stateMachine,
                          OwnerContextService ownerContextService,
                          TenantGuard tenantGuard,
                          PermissionEvaluator permissionEvaluator,
                          CommandProducer commandProducer) {
        this.commandRepository = commandRepository;
        this.machineRepository = machineRepository;
        this.deviceRepository = deviceRepository;
        this.stateMachine = stateMachine;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.commandProducer = commandProducer;
    }

    @Transactional
    public CommandResponse createCommand(CommandRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID issuedBy = permissionEvaluator.getCurrentUserId();

        // Validate machine exists and belongs to tenant
        Machine machine = machineRepository.findById(request.getMachineId())
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + request.getMachineId()));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        // Find device bound to this machine (if any)
        UUID deviceId = deviceRepository.findByOrganizationIdAndImei(orgId, machine.getSerialNumber())
                .map(d -> d.getId())
                .orElse(null);

        MachineCommand command = new MachineCommand();
        command.setOrganizationId(orgId);
        command.setMachineId(machine.getId());
        command.setDeviceId(deviceId);
        command.setIssuedBy(issuedBy);
        command.setCommandType(request.getCommandType());
        command.setStatus(CommandStateMachine.CommandState.PENDING.name());
        command.setAttemptCount(0);
        command.setMaxAttempts(3);

        command = commandRepository.save(command);
        log.info("Created command id={} machineId={} type={} issuedBy={}",
                command.getId(), command.getMachineId(), command.getCommandType(), issuedBy);

        // Publish command to RabbitMQ for the device gateway to consume.
        // Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
        // Per AGENTS.md rule 5: never assume success until ack is received.
        String imei = machine.getSerialNumber();
        commandProducer.sendCommand(command.getId(), command.getMachineId(), imei, command.getCommandType());

        return toResponse(command);
    }

    @Transactional(readOnly = true)
    public CommandResponse getCommand(UUID id) {
        MachineCommand command = commandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + id));
        tenantGuard.validateTenantAccess(command.getOrganizationId());
        return toResponse(command);
    }

    @Transactional(readOnly = true)
    public CommandStatusDto getCommandStatus(UUID id) {
        MachineCommand command = commandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + id));
        tenantGuard.validateTenantAccess(command.getOrganizationId());
        return new CommandStatusDto(
                command.getId(),
                command.getStatus(),
                command.getAttemptCount(),
                command.getMaxAttempts(),
                command.getLastError(),
                command.getCompletedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<CommandResponse> listCommands(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return commandRepository.findByOrganizationId(orgId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CommandResponse> listCommandsByMachine(UUID machineId, Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return commandRepository.findByOrganizationIdAndMachineId(orgId, machineId, pageable)
                .map(this::toResponse);
    }

    /**
     * Transitions a command to a new state.
     * Called by the RabbitMQ consumer when ack/timeout/failure is received from the gateway.
     */
    @Transactional
    public CommandResponse transitionCommand(UUID commandId, String targetState, String error) {
        MachineCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        CommandStateMachine.CommandState current = stateMachine.fromString(command.getStatus());
        CommandStateMachine.CommandState target = stateMachine.fromString(targetState);

        stateMachine.validateTransition(current, target);

        command.setStatus(target.name());
        if (error != null) {
            command.setLastError(error);
        }
        if (stateMachine.isTerminal(target)) {
            command.setCompletedAt(LocalDateTime.now());
        }

        command = commandRepository.save(command);
        log.info("Command {} transitioned: {} → {}", commandId, current, target);
        return toResponse(command);
    }

    /**
     * Records a new attempt for a command and increments the attempt count.
     * Called when a command is sent to the gateway.
     */
    @Transactional
    public void recordAttempt(UUID commandId, int attemptNumber, String status, String error) {
        MachineCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));

        command.setAttemptCount(command.getAttemptCount() + 1);
        if (error != null) {
            command.setLastError(error);
        }
        commandRepository.save(command);

        log.info("Recorded attempt {} for command {} status={}", attemptNumber, commandId, status);
    }

    private CommandResponse toResponse(MachineCommand c) {
        return new CommandResponse(
                c.getId(),
                c.getOrganizationId(),
                c.getMachineId(),
                c.getDeviceId(),
                c.getIssuedBy(),
                c.getCommandType(),
                c.getStatus(),
                c.getAttemptCount(),
                c.getMaxAttempts(),
                c.getLastError(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCompletedAt()
        );
    }
}
