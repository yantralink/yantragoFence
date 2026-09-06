package com.yantrago.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Simulator configuration — device count, intervals, and target host/port.
 *
 * Configurable via application.yml under the 'simulator' prefix.
 *
 * Example:
 * simulator:
 *   target-host: localhost
 *   concox-port: 5000
 *   jt808-port: 5001
 *   fencing-port: 5002
 *   device-count: 1
 *   heartbeat-interval-ms: 10000
 *   gps-interval-ms: 30000
 *   telemetry-interval-ms: 60000
 *   start-imei: 867010070113452
 *   enabled-protocols: concox,fencing
 */
@Configuration
@ConfigurationProperties(prefix = "simulator")
public class SimConfig {

    private String targetHost = "localhost";
    private int concoxPort = 5000;
    private int jt808Port = 5001;
    private int fencingPort = 5002;

    private int deviceCount = 1;
    private long heartbeatIntervalMs = 10000;
    private long gpsIntervalMs = 30000;
    private long telemetryIntervalMs = 60000;

    private long startImei = 867010070113452L;
    private String enabledProtocols = "concox,fencing";

    // Getters and setters

    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }

    public int getConcoxPort() { return concoxPort; }
    public void setConcoxPort(int concoxPort) { this.concoxPort = concoxPort; }

    public int getJt808Port() { return jt808Port; }
    public void setJt808Port(int jt808Port) { this.jt808Port = jt808Port; }

    public int getFencingPort() { return fencingPort; }
    public void setFencingPort(int fencingPort) { this.fencingPort = fencingPort; }

    public int getDeviceCount() { return deviceCount; }
    public void setDeviceCount(int deviceCount) { this.deviceCount = deviceCount; }

    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

    public long getGpsIntervalMs() { return gpsIntervalMs; }
    public void setGpsIntervalMs(long gpsIntervalMs) { this.gpsIntervalMs = gpsIntervalMs; }

    public long getTelemetryIntervalMs() { return telemetryIntervalMs; }
    public void setTelemetryIntervalMs(long telemetryIntervalMs) { this.telemetryIntervalMs = telemetryIntervalMs; }

    public long getStartImei() { return startImei; }
    public void setStartImei(long startImei) { this.startImei = startImei; }

    public String getEnabledProtocols() { return enabledProtocols; }
    public void setEnabledProtocols(String enabledProtocols) { this.enabledProtocols = enabledProtocols; }

    public boolean isConcoxEnabled() { return enabledProtocols.contains("concox"); }
    public boolean isJt808Enabled() { return enabledProtocols.contains("jt808"); }
    public boolean isFencingEnabled() { return enabledProtocols.contains("fencing"); }
}
