package com.yantrago.api.controller;

import com.yantrago.api.model.MachineSetting;
import com.yantrago.api.model.SystemSetting;
import com.yantrago.api.service.SettingsService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settings endpoints — manage machine settings and system settings.
 *
 * Machine settings:
 *   GET    /api/v1/settings/machines/{machineId} — list all settings for a machine
 *   GET    /api/v1/settings/machines/{machineId}/{key} — get a specific setting
 *   PUT    /api/v1/settings/machines/{machineId}/{key} — upsert a setting
 *   DELETE /api/v1/settings/machines/{machineId}/{key} — delete a setting
 *
 * System settings:
 *   GET /api/v1/settings/system — list system settings
 *   GET /api/v1/settings/system/{key} — get a specific system setting
 *   PUT /api/v1/settings/system/{key} — upsert a system setting
 */
@RestController
@RequestMapping("/api/v1/settings")
@Validated
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    // ===== Machine Settings =====

    @GetMapping("/machines/{machineId}")
    public ResponseEntity<List<MachineSetting>> getMachineSettings(@PathVariable UUID machineId) {
        return ResponseEntity.ok(settingsService.getMachineSettings(machineId));
    }

    @GetMapping("/machines/{machineId}/{key}")
    public ResponseEntity<MachineSetting> getMachineSetting(@PathVariable UUID machineId,
                                                            @PathVariable String key) {
        return ResponseEntity.ok(settingsService.getMachineSetting(machineId, key));
    }

    @PutMapping("/machines/{machineId}/{key}")
    public ResponseEntity<MachineSetting> upsertMachineSetting(@PathVariable UUID machineId,
                                                               @PathVariable String key,
                                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(settingsService.upsertMachineSetting(
                machineId, key,
                body.get("value"),
                body.getOrDefault("dataType", "STRING"),
                body.get("description")));
    }

    @DeleteMapping("/machines/{machineId}/{key}")
    public ResponseEntity<Void> deleteMachineSetting(@PathVariable UUID machineId,
                                                     @PathVariable String key) {
        settingsService.deleteMachineSetting(machineId, key);
        return ResponseEntity.noContent().build();
    }

    // ===== System Settings =====

    @GetMapping("/system")
    public ResponseEntity<List<SystemSetting>> getSystemSettings() {
        return ResponseEntity.ok(settingsService.getSystemSettings());
    }

    @GetMapping("/system/{key}")
    public ResponseEntity<SystemSetting> getSystemSetting(@PathVariable String key) {
        return ResponseEntity.ok(settingsService.getSystemSetting(key));
    }

    @PutMapping("/system/{key}")
    public ResponseEntity<SystemSetting> upsertSystemSetting(@PathVariable String key,
                                                             @RequestBody Map<String, String> body) {
        Boolean isSensitive = body.get("isSensitive") != null
                ? Boolean.valueOf(body.get("isSensitive"))
                : null;
        return ResponseEntity.ok(settingsService.upsertSystemSetting(
                key,
                body.get("value"),
                body.getOrDefault("dataType", "STRING"),
                body.get("description"),
                isSensitive));
    }
}
