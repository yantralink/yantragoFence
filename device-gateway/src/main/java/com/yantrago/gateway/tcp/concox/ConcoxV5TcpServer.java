package com.yantrago.gateway.tcp.concox;

import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.gateway.tcp.ProtocolHandler;
import com.yantrago.gateway.tcp.ProtocolRouter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConcoxV5TcpServer {

    private static final Logger log = LoggerFactory.getLogger(ConcoxV5TcpServer.class);

    @Value("${tcp.server.port:5000}")
    private int port;

    private ServerSocket serverSocket;
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    private final ProtocolRouter protocolRouter;
    private final DeviceConnectionRegistry connectionRegistry;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private Counter packetsProcessedCounter;

    public ConcoxV5TcpServer(ProtocolRouter protocolRouter,
                              DeviceConnectionRegistry connectionRegistry,
                              MeterRegistry meterRegistry) {
        this.protocolRouter = protocolRouter;
        this.connectionRegistry = connectionRegistry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        running = true;
        packetsProcessedCounter = Counter.builder("tcp.packets.processed")
                .description("Total TCP packets processed")
                .register(meterRegistry);
        meterRegistry.gauge("tcp.active.connections", activeConnections);
        new Thread(this::acceptConnections, "TCP-Server").start();
        log.info("[TCP] Concox V5 server starting on port {}", port);
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("[TCP] Error closing server socket", e);
        }
        clientExecutor.shutdown();
    }

    private void acceptConnections() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("[TCP] Server listening on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log.info("[TCP] Client connected: {}", clientSocket.getInetAddress());
                    clientExecutor.submit(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (running) log.error("[TCP] Socket error in accept loop", e);
                }
            }
        } catch (IOException e) {
            log.error("[TCP] Error in acceptConnections", e);
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

                // Log raw hex data for debugging
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < bytesRead; i++) {
                    hex.append(String.format("%02X ", data[i]));
                }
                log.debug("[TCP] Raw data from {} ({} bytes): {}", clientId, bytesRead, hex);

                // Parse and handle packets
                byte[][] packets = extractPackets(data);
                log.debug("[TCP] Extracted {} packets from {} bytes", packets.length, bytesRead);
                for (byte[] packet : packets) {
                    packetsProcessedCounter.increment();
                    byte[] response = protocolRouter.handlePacket(packet, clientId);
                    if (response != null) {
                        StringBuilder respHex = new StringBuilder();
                        for (byte b : response) respHex.append(String.format("%02X ", b));
                        log.debug("[TCP] Sending response ({} bytes): {}", response.length, respHex);
                        out.write(response);
                        out.flush();
                    }
                }

                // Register connection after first packet (login) so we have the IMEI mapping
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
            log.info("[TCP] Client disconnected: {} - {}", clientId, e.getMessage());
        } finally {
            activeConnections.decrementAndGet();
            if (registered) {
                connectionRegistry.unregisterConnection(clientId);
            }
            // Clean up protocol handler's client-IMEI mapping to prevent memory leaks
            if (handler != null) {
                handler.removeClient(clientId);
            }
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("[TCP] Error closing client socket: {}", e.getMessage());
            }
        }
    }

    private byte[][] extractPackets(byte[] data) {
        // Find all packets starting with 0x78 0x78 or 0x79 0x79
        java.util.List<byte[]> packets = new java.util.ArrayList<>();

        int i = 0;
        while (i < data.length - 1) {
            // Check for start bits
            if ((data[i] == 0x78 && data[i + 1] == 0x78) ||
                (data[i] == 0x79 && data[i + 1] == 0x79)) {

                boolean extended = data[i] == 0x79;
                int lengthOffset = extended ? 5 : 2; // 0x79 has 2-byte length, 0x78 has 1-byte

                if (i + lengthOffset >= data.length) break;

                int packetLength;
                if (extended) {
                    packetLength = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
                } else {
                    packetLength = data[i + 2] & 0xFF;
                }

                // Total packet size = start(2) + length(1 or 2) + content(length, includes CRC) + stop(2)
                int totalLength = (extended ? 4 : 3) + packetLength + 2;

                if (i + totalLength <= data.length) {
                    byte[] packet = new byte[totalLength];
                    System.arraycopy(data, i, packet, 0, totalLength);
                    packets.add(packet);
                    i += totalLength;
                } else {
                    break;
                }
            } else {
                i++;
            }
        }

        return packets.toArray(new byte[0][]);
    }
}
