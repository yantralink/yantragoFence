package com.yantrago.gateway.tcp.jt808;

import com.yantrago.gateway.tcp.DashcamConnectionRegistry;
import com.yantrago.gateway.service.DashcamService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;

@Component
@ConditionalOnProperty(name = "jt808.tcp.enabled", havingValue = "true", matchIfMissing = true)
public class JT808TcpServer {

    private static final Logger log = LoggerFactory.getLogger(JT808TcpServer.class);

    @Value("${jt808.tcp.port:5001}")
    private int port;

    // SEC-11: Bounded thread pool to prevent resource exhaustion
    @Value("${jt808.tcp.max-connections:100}")
    private int maxConnections;

    // SEC-11: Pre-authentication deadline
    @Value("${jt808.tcp.login-timeout-ms:30000}")
    private int loginTimeoutMs;

    // SEC-11: Max idle lifetime
    @Value("${jt808.tcp.max-idle-timeout-ms:600000}")
    private int maxIdleTimeoutMs;

    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private volatile boolean running = false;

    private final JT808FrameParser frameParser;
    private final JT808ProtocolHandler protocolHandler;
    private final DashcamConnectionRegistry connectionRegistry;

    public JT808TcpServer(JT808FrameParser frameParser,
                           JT808ProtocolHandler protocolHandler,
                           DashcamConnectionRegistry connectionRegistry) {
        this.frameParser = frameParser;
        this.protocolHandler = protocolHandler;
        this.connectionRegistry = connectionRegistry;
    }

    @PostConstruct
    public void start() {
        running = true;
        // SEC-11: Bounded thread pool.
        clientExecutor = Executors.newFixedThreadPool(maxConnections);
        new Thread(this::acceptConnections, "JT808-TCP-Server").start();
        log.info("[JT808] TCP server starting on port {} (maxConnections={})", port, maxConnections);
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("[JT808] Error closing server socket", e);
        }
        clientExecutor.shutdownNow();
    }

    private void acceptConnections() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("[JT808] Server listening on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // SEC-11: Reject connections beyond the max limit.
                    if (activeConnections.get() >= maxConnections) {
                        log.warn("SEC-11: [JT808] Connection rejected: max connections ({}) reached from {}",
                                maxConnections, clientSocket.getInetAddress());
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            log.debug("[JT808] Error closing rejected connection: {}", e.getMessage());
                        }
                        continue;
                    }
                    log.info("[JT808] T98 connected: {}", clientSocket.getInetAddress());
                    clientExecutor.submit(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (running) log.error("[JT808] Socket error in accept loop", e);
                }
            }
        } catch (IOException e) {
            log.error("[JT808] Error in acceptConnections", e);
        }
    }

    private void handleClient(Socket socket) {
        String clientId = socket.getInetAddress() + ":" + socket.getPort();
        boolean registered = false;
        activeConnections.incrementAndGet();
        long connectionStartTime = System.currentTimeMillis();
        long lastActivityTime = connectionStartTime;
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            socket.setKeepAlive(true);
            socket.setSoTimeout(180000);

            byte[] buffer = new byte[65536];

            while (running) {
                int bytesRead;
                try {
                    bytesRead = in.read(buffer);
                } catch (SocketTimeoutException ste) {
                    long now = System.currentTimeMillis();
                    // SEC-11: Pre-authentication deadline.
                    if (!registered && (now - connectionStartTime) > loginTimeoutMs) {
                        log.warn("SEC-11: [JT808] Closing unauthenticated connection from {} (no login within {}ms)",
                                clientId, loginTimeoutMs);
                        break;
                    }
                    // SEC-11: Max idle lifetime.
                    if (registered && (now - lastActivityTime) > maxIdleTimeoutMs) {
                        log.warn("SEC-11: [JT808] Closing idle connection from {} (idle > {}ms)",
                                clientId, maxIdleTimeoutMs);
                        break;
                    }
                    log.debug("[JT808] Read timeout from {} (idle between packets)", clientId);
                    continue;
                }
                if (bytesRead == -1) {
                    break;
                }
                lastActivityTime = System.currentTimeMillis();
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);

                if (log.isDebugEnabled()) {
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(bytesRead, 64); i++) {
                        hex.append(String.format("%02X ", data[i]));
                    }
                    log.debug("[JT808] Raw data from {} ({} bytes): {}", clientId, bytesRead, hex);
                }

                List<byte[]> frames = frameParser.extractFrames(data);
                for (byte[] frame : frames) {
                    byte[] response = protocolHandler.handleFrame(frame, clientId, out);

                    if (!registered) {
                        String simPhone = protocolHandler.getSimPhoneForClient(clientId);
                        if (simPhone != null) {
                            connectionRegistry.registerConnection(clientId, simPhone, out);
                            registered = true;
                        }
                    }

                    if (response != null) {
                        out.write(response);
                        out.flush();
                    }
                }
            }
        } catch (IOException e) {
            log.info("[JT808] T98 disconnected: {} - {}", clientId, e.getMessage());
        } finally {
            activeConnections.decrementAndGet();
            connectionRegistry.unregisterConnection(clientId);
            protocolHandler.removeClient(clientId);
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("[JT808] Error closing socket: {}", e.getMessage());
            }
        }
    }

    private final java.util.concurrent.atomic.AtomicInteger activeConnections = new java.util.concurrent.atomic.AtomicInteger(0);
}
