package com.yantrago.api.service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Time abstraction for testable time-dependent services.
 * Tests can provide a fixed Clock to verify threshold/window behavior.
 */
public interface TimeProvider {

    LocalDateTime now();

    Clock clock();
}
