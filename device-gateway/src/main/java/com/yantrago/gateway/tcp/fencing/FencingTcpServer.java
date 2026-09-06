package com.yantrago.gateway.tcp.fencing;

import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.gateway.tcp.ProtocolHandler;
import com.yantrago.gateway.tcp.ProtocolRouter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP server for YantraGO fencing machines.
 *
 * Listens on port 5002 (configurable via fencing.tcp.port).
 * Follows the same pattern as ConcoxV5TcpServer — accepts connections,
 * reads packets, routes them through ProtocolRouter, and sends responses.
 *
 * Per AGENTS.md rule 3: TCP protocol handling is in the gateway, not REST controllers.
 */
@Component
@ConditionalOnProperty(name = "fencing.tcp.enabled", havingValue = "true", matchIfMissing = true)
public class FencingTcpServer {

    private static final Logger log = LoggerFactory.getLogger(FencingTcpServer.class);

    @Value("${fencing.tcp.port:5002}")
    private int port;

    private ServerSocket serverSocket;
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    private final ProtocolRouter protocolRouter;
    private final DeviceConnectionRegistry connectionRegistry;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private Counter packetsProcessedCounter;

    public FencingTcpServer(ProtocolRouter protocolRouter,
                             DeviceConnectionRegistry connectionRegistry,
                             MeterRegistry meterRegistry) {
        this.protocolRouter = protocolRouter;
        this.connectionRegistry = connectionRegistry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        running = true;
        packetsProcessedCounter = Counter.builder("fencing.tcp.packets.processed")
                .description("Total fencing TCP packets processed")
                .register(meterRegistry);
        meterRegistry.gauge("fencing.tcp.active.connections", activeConnections);
        new Thread(this::acceptConnections, "Fencing-TCP-Server").start();
        log.info("[Fencing] TCP server starting on port {}", port);
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("[Fencing] Error closing server socket", e);
        }
        clientExecutor.shutdown();
    }

    private void acceptConnections() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("[Fencing] Server listening on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log.info("[Fencing] Device connected: {}", clientSocket.getInetAddress());
                    clientExecutor.submit(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (running) log.error("[Fencing] Socket error in accept loop", e);
                }
            }
        } catch (IOException e) {
            log.error("[Fencing] Error in acceptConnections", e);
        }
    }

    private void handleClient(Socket socket) {
        String clientId = socket.getInetAddress() + ":" + socket.getPort();
        boolean registered = false;
        ProtocolHandler handler = null;
        activeConnections.incrementAndGet();

        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            socket.setKeepAlive(true);
            socket.setSoTimeout(60000); // 60s timeout

            byte[] buffer = new byte[1024];
            int bytesRead;

            while (running && (bytesRead = in.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);

                if (log.isDebugEnabled()) {
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < bytesRead; i++) {
                        hex.append(String.format("%02X ", data[i]));
                    }
                    log.debug("[Fencing] Raw data from {} ({} bytes): {}", clientId, bytesRead, hex);
                }

                // Extract and handle packets
                byte[][] packets = extractPackets(data);
                log.debug("[Fencing] Extracted {} packets from {} bytes", packets.length, bytesRead);

                for (byte[] packet : packets) {
                    packetsProcessedCounter.increment();
                    byte[] response = protocolRouter.handlePacket(packet, clientId);
                    if (response != null) {
                        out.write(response);
                        out.flush();
                    }
                }

                // Register connection after login packet
                if (!registered) {
                    handler = protocolRouter.findHandler(data);
                    if (handler != null) {
                        String imei = handler.getImeiForClient(clientId);
                        if (imei != null) {
                            connectionRegistry.registerConnection(clientId, imei, out);
                            registered = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.info("[Fencing] Device disconnected: {} - {}", clientId, e.getMessage());
        } finally {
            activeConnections.decrementAndGet();
            if (registered) {
                connectionRegistry.unregisterConnection(clientId);
            }
            if (handler != null) {
                handler.removeClient(clientId);
            }
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("[Fencing] Error closing socket: {}", e.getMessage());
            }
        }
    }

    /**
     * Extracts individual packets from a TCP data stream.
     * Fencing packets start with 0xAA 0x55 and end with 0x0D 0x0A.
     */
    private byte[][] extractPackets(byte[] data) {
        java.util.List<byte[]> packets = new java.util.ArrayList<>();

        int i = 0;
        while (i < data.length - 1) {
            // Check for start bytes 0xAA 0x55
            if (data[i] == FencingConstants.START_BYTES[0]
                    && data[i + 1] == FencingConstants.START_BYTES[1]) {

                // Find stop bytes 0x0D 0x0A
                int end = -1;
                for (int j = i + 2; j < data.length - 1; j++) {
                    if (data[j] == FencingConstants.STOP_BYTES[0]
                            && data[j + 1] == FencingConstants.STOP_BYTES[1]) {
                        end = j + 1; // Include stop bytes
                        break;
                    }
                }

                if (end != -1) {
                    int len = end - i + 1;
                    byte[] packet = new byte[len];
                    System.arraycopy(data, i, packet, 0, len);
                    packets.add(packet);
                    i = end + 1;
                } else {
                    // Incomplete packet — wait for more data
                    break;
                }
            } else {
                i++;
            }
        }

        return packets.toArray(new byte[0][]);
    }
}
