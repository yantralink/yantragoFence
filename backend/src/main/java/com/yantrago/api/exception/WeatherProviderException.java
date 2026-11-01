package com.yantrago.api.exception;

/**
 * Thrown when the upstream weather provider cannot satisfy a request
 * (unreachable, error response, missing credentials, or the provider
 * throttle cannot admit the call within the bounded wait). Mapped to
 * HTTP 502 by GlobalExceptionHandler — provider outages must not
 * surface as 500s or leak provider internals to the client.
 */
public class WeatherProviderException extends RuntimeException {
    public WeatherProviderException(String message) {
        super(message);
    }

    public WeatherProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
