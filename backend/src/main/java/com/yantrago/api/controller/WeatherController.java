package com.yantrago.api.controller;

import com.yantrago.api.dto.weather.WeatherDto;
import com.yantrago.api.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * SEC-15: backend weather proxy endpoint.
 *
 * GET /api/v1/machines/{machineId}/weather
 *
 * The client identifies the machine only — coordinates are resolved
 * server-side so the endpoint cannot be used to query weather for
 * arbitrary locations, and machine access is enforced by the same
 * tenant/customer checks as location reads. Weather derives from the
 * location resource, so it reuses the `location:read` permission
 * rather than inventing a new one.
 */
@RestController
@RequestMapping("/api/v1/machines")
@Validated
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/{machineId}/weather")
    @PreAuthorize("hasAuthority('location:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<WeatherDto> getWeather(@PathVariable UUID machineId) {
        WeatherDto weather = weatherService.getCurrentWeather(machineId);
        if (weather == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(weather);
    }
}
