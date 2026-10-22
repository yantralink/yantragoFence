package com.yantrago.api.dto.geocoding;

/**
 * Reverse-geocoded address returned by the backend geocoding proxy.
 *
 * SEC-15: the client never talks to a geocoding provider directly — this
 * DTO is the sanitized projection of the upstream provider response
 * (currently Nominatim jsonv2). Null fields are omitted from JSON via the
 * global Jackson non_null inclusion.
 */
public record AddressDto(
        String displayName,
        String road,
        String neighbourhood,
        String suburb,
        String city,
        String county,
        String state,
        String postcode,
        String country
) {}
