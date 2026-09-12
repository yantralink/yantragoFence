package com.yantrago.api.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Provides a Clock for time-dependent services. Tests can override via
 * @MockBean or by providing a fixed Clock in test configuration.
 */
@Component
public class SystemTimeProvider implements TimeProvider {

    private final Clock clock = Clock.systemUTC();

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @Override
    public Clock clock() {
        return clock;
    }
}
