package com.yantrago.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway configuration — TCP port bindings, timeouts, and thread pool settings.
 *
 * Ports are read from application.yml and injected into the TCP server classes
 * via @Value annotations. This class provides a central place for gateway-specific
 * configuration properties.
 *
 * Per AGENTS.md rule 20: no secrets committed — all sensitive values come from env vars.
 */
@Configuration
public class GatewayConfig {

    @Value("${tcp.server.port:5000}")
    private int concoxPort;

    @Value("${jt808.tcp.port:5001}")
    private int jt808Port;

    @Value("${fencing.tcp.port:5002}")
    private int fencingPort;

    @Value("${jt808.tcp.enabled:true}")
    private boolean jt808Enabled;

    @Value("${fencing.tcp.enabled:true}")
    private boolean fencingEnabled;

    @Value("${jt1076.rtp.enabled:false}")
    private boolean jt1076RtpEnabled;

    @Value("${jt1076.rtp.port:5003}")
    private int jt1076RtpPort;

    public int getConcoxPort() { return concoxPort; }
    public int getJt808Port() { return jt808Port; }
    public int getFencingPort() { return fencingPort; }
    public boolean isJt808Enabled() { return jt808Enabled; }
    public boolean isFencingEnabled() { return fencingEnabled; }
    public boolean isJt1076RtpEnabled() { return jt1076RtpEnabled; }
    public int getJt1076RtpPort() { return jt1076RtpPort; }
}
