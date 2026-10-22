package com.yantrago.api.controller;

import com.yantrago.api.dto.geocoding.AddressDto;
import com.yantrago.api.service.GeocodingService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * SEC-15: backend geocoding proxy endpoint.
 *
 * GET /api/v1/geocoding/reverse?machineId=...&lang=...
 *
 * The client identifies the machine only — coordinates are resolved
 * server-side so the endpoint cannot be used to geocode arbitrary
 * locations, and machine access is enforced by the same tenant/customer
 * checks as location reads. `lang` is constrained to a BCP-47-ish
 * language tag so it can be safely forwarded as Accept-Language.
 */
@RestController
@RequestMapping("/api/v1/geocoding")
@Validated
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/reverse")
    @PreAuthorize("hasAuthority('location:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<AddressDto> reverseGeocode(
            @RequestParam UUID machineId,
            @RequestParam(defaultValue = "en")
            @Pattern(regexp = "^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$", message = "invalid language tag")
            String lang) {
        AddressDto address = geocodingService.reverseGeocode(machineId, lang);
        if (address == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(address);
    }
}
