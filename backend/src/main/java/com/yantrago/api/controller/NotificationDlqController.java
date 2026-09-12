package com.yantrago.api.controller;

import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.service.NotificationDlqReplayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * DLQ replay endpoint — privileged, audited, bounded, idempotent.
 *
 * Per notification plan Phase 6 / N11:
 * - "Replays are privileged, audited, bounded, and idempotent."
 * - "Define ownership for DLQ review and safe replay."
 *
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Only admin roles with notification:replay permission can replay DLQ messages.
 */
@RestController
@RequestMapping("/api/v1/notifications/dlq")
public class NotificationDlqController {

    private final NotificationDlqReplayService replayService;
    private final PermissionEvaluator permissionEvaluator;

    public NotificationDlqController(NotificationDlqReplayService replayService,
                                       PermissionEvaluator permissionEvaluator) {
        this.replayService = replayService;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * Replays a single DLQ message back to the notification queue.
     * The message body is the original DLQ message JSON.
     */
    @PostMapping("/replay")
    @PreAuthorize("hasAuthority('notification:replay') or hasRole('super_admin')")
    public ResponseEntity<NotificationDlqReplayService.ReplayResult> replayMessage(
            @Valid @RequestBody ReplayRequest request) {
        var userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        var result = replayService.replayMessage(
                request.messageBody(), userId, request.reason());
        return ResponseEntity.ok(result);
    }

    public record ReplayRequest(
            @NotBlank String messageBody,
            @NotBlank String reason
    ) {}
}
