package com.yantrago.api.controller;

import com.yantrago.api.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * PLAY-06: self-service account endpoints.
 *
 * POST /api/v1/account/deletion-request — records the authenticated
 * user's request for account erasure. Accounts are admin-provisioned, so
 * actual deletion is performed by an admin; this endpoint is the
 * in-app discoverable path required by the Play account-deletion policy.
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/deletion-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> requestDeletion(HttpServletRequest request) {
        accountService.recordDeletionRequest(request);
        return ResponseEntity.accepted().body(Map.of(
                "status", "received",
                "message", "Your account deletion request has been recorded. "
                        + "Support will process it within 30 days."));
    }
}
