package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch insert service for location_history (partitioned by month).
 *
 * Adapted from HarvestTracker's LocationPersistenceService pattern:
 * - ConcurrentLinkedQueue buffer
 * - Scheduled flush every 5 seconds
 * - Batch insert via JdbcTemplate.batchUpdate()
 * - Max queue size guard (5000)
 *
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Service
public class LocationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(LocationPersistenceService.class);

    private static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_SECONDS = 5L;
    private static final int MAX_QUEUE_SIZE = 5000;

    private final ConcurrentLinkedQueue<LocationRecord> locationBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger bufferedCount = new AtomicInteger(0);
    private ScheduledExecutorService flushScheduler;

    private final JdbcTemplate jdbcTemplate;

    public LocationPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "location-history-flush");
            t.setDaemon(true);
            return t;
        });
        flushScheduler.scheduleWithFixedDelay(
                this::flushLocationBuffer,
                FLUSH_INTERVAL_SECONDS,
                FLUSH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @PreDestroy
    public void shutdown() {
        flushLocationBuffer();
        if (flushScheduler != null) {
            flushScheduler.shutdown();
            try {
                if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    flushScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                flushScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Adds a location record to the buffer for batch insertion.
     */
    public void addToBuffer(UUID id, UUID organizationId, UUID deviceId, UUID machineId,
                            String imei, Double latitude, Double longitude,
                            Double speed, Double course, LocalDateTime recordedAt) {
        if (bufferedCount.get() >= MAX_QUEUE_SIZE) {
            log.warn("Location buffer full ({}), flushing immediately", MAX_QUEUE_SIZE);
            flushLocationBuffer();
        }

        locationBuffer.offer(new LocationRecord(id, organizationId, deviceId, machineId,
                imei, latitude, longitude, speed, course, recordedAt));
        int currentSize = bufferedCount.incrementAndGet();

        if (currentSize >= BATCH_SIZE) {
            flushLocationBuffer();
        }
    }

    /**
     * Flushes the buffer to the database via batch insert.
     */
    @Transactional
    public void flushLocationBuffer() {
        List<LocationRecord> batch = new ArrayList<>(BATCH_SIZE);
        int count = 0;

        while (count < BATCH_SIZE) {
            LocationRecord record = locationBuffer.poll();
            if (record == null) {
                break;
            }
            batch.add(record);
            count++;
        }

        if (batch.isEmpty()) {
            return;
        }

        bufferedCount.addAndGet(-count);

        try {
            String sql = "INSERT INTO location_history " +
                    "(id, organization_id, device_id, machine_id, imei, latitude, longitude, " +
                    "speed, course, recorded_at, received_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

            jdbcTemplate.batchUpdate(sql, batch, batch.size(), (ps, record) -> {
                ps.setObject(1, record.id());
                ps.setObject(2, record.organizationId());
                ps.setObject(3, record.deviceId());
                ps.setObject(4, record.machineId());
                ps.setString(5, record.imei());
                ps.setDouble(6, record.latitude());
                ps.setDouble(7, record.longitude());
                if (record.speed() != null) {
                    ps.setDouble(8, record.speed());
                } else {
                    ps.setNull(8, java.sql.Types.DOUBLE);
                }
                if (record.course() != null) {
                    ps.setDouble(9, record.course());
                } else {
                    ps.setNull(9, java.sql.Types.DOUBLE);
                }
                ps.setTimestamp(10, java.sql.Timestamp.valueOf(record.recordedAt()));
            });

            log.debug("Flushed {} location history records to DB", count);
        } catch (Exception e) {
            log.error("Failed to batch insert location history ({} records)", count, e);
        }
    }

    /**
     * Record for batch insertion.
     */
    record LocationRecord(
            UUID id,
            UUID organizationId,
            UUID deviceId,
            UUID machineId,
            String imei,
            Double latitude,
            Double longitude,
            Double speed,
            Double course,
            LocalDateTime recordedAt
    ) {}
}
