package com.yantrago.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer custom metrics configuration.
 *
 * Defines custom counters and timers for business operations:
 * - commands.created: total commands created
 * - commands.completed: total commands completed (DONE state)
 * - commands.failed: total commands failed (FAILED state)
 * - telemetry.ingested: total telemetry readings ingested
 * - alerts.generated: total alerts generated
 * - notifications.dispatched: total notifications dispatched
 *
 * Metrics are exposed via Prometheus at /actuator/prometheus.
 */
@Configuration
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public Counter commandsCreatedCounter() {
        return Counter.builder("commands.created")
                .description("Total commands created")
                .register(meterRegistry);
    }

    @Bean
    public Counter commandsCompletedCounter() {
        return Counter.builder("commands.completed")
                .description("Total commands completed (DONE state)")
                .register(meterRegistry);
    }

    @Bean
    public Counter commandsFailedCounter() {
        return Counter.builder("commands.failed")
                .description("Total commands failed (FAILED state)")
                .register(meterRegistry);
    }

    @Bean
    public Counter telemetryIngestedCounter() {
        return Counter.builder("telemetry.ingested")
                .description("Total telemetry readings ingested")
                .register(meterRegistry);
    }

    @Bean
    public Counter alertsGeneratedCounter() {
        return Counter.builder("alerts.generated")
                .description("Total alerts generated")
                .register(meterRegistry);
    }

    @Bean
    public Counter notificationsDispatchedCounter() {
        return Counter.builder("notifications.dispatched")
                .description("Total notifications dispatched")
                .register(meterRegistry);
    }

    @Bean
    public Timer commandLifecycleTimer() {
        return Timer.builder("command.lifecycle")
                .description("Command lifecycle duration (create to completion)")
                .register(meterRegistry);
    }
}
