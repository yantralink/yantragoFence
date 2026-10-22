package com.yantrago.api.exception;

/**
 * Thrown when the upstream geocoding provider cannot satisfy a request
 * (unreachable, error response, or the provider throttle cannot admit the
 * call within the bounded wait). Mapped to HTTP 502 by
 * GlobalExceptionHandler — provider outages must not surface as 500s or
 * leak provider internals to the client.
 */
public class GeocodingProviderException extends RuntimeException {
    public GeocodingProviderException(String message) {
        super(message);
    }

    public GeocodingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
