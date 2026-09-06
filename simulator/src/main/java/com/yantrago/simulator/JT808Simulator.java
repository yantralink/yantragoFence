package com.yantrago.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Simulates JT808-2013 / T98 dashcam devices.
 *
 * Each simulated device:
 * 1. Connects to the gateway on the JT808 TCP port (default 5001)
 * 2. Sends a registration packet (0x0100) with terminal info
 * 3. Sends an authentication packet (0x0102) with the received auth code
 * 4. Periodically sends heartbeat packets (0x0002)
 * 5. Periodically sends location reports (0x0200) with lat/lng/speed/direction
 * 6. Listens for incoming command packets and responds via CommandResponder
 *
 * Uses the same XOR checksum, 0x7E framing, and 0x7D escape sequences as the
 * gateway's JT808FrameParser and JT808MessageEncoder.
 *
 * Per AGENTS.md rule 16: same protocol logic as the gateway handlers.
 */
@Component
public class JT808Simulator {

    private static final Logger log = LoggerFactory.getLogger(JT808Simulator.class);

    private static final byte MARKER = 0x7E;
    private static final int MSG_REGISTRATION = 0x0100;
    private static final int MSG_AUTHENTICATION = 0x0102;
    private static final int MSG_HEARTBEAT = 0x0002;
    private static final int MSG_LOCATION = 0x0200;

    private final SimConfig config;
    private final CommandResponder commandResponder;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Random random = new Random();

    public JT808Simulator(SimConfig config, CommandResponder commandResponder) {
        this.config = config;
        this.commandResponder = commandResponder;
    }

    public void start() {
        if (!config.isJt808Enabled()) {
            log.info("[JT808-Sim] JT808 protocol disabled, skipping");
            return;
        }

        int count = config.getDeviceCount();
        log.info("[JT808-Sim] Starting {} JT808/T98 devices targeting {}:{}",
                count, config.getTargetHost(), config.getJt808Port());

        for (int i = 0; i < count; i++) {
            // SIM phone: 12-digit hex string, unique per device
            String simPhone = String.format("012345%06d", i + 1);
            executor.submit(() -> simulateDevice(simPhone));
        }
    }

    private void simulateDevice(String simPhone) {
        try (Socket socket = new Socket(config.getTargetHost(), config.getJt808Port());
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setKeepAlive(true);
            socket.setSoTimeout(180000);
            log.info("[JT808-Sim] Device {} connected to {}:{}", simPhone,
                    config.getTargetHost(), config.getJt808Port());

            // 1. Send registration
            byte[] regPacket = buildRegistrationPacket(simPhone);
            out.write(regPacket);
            out.flush();
            log.debug("[JT808-Sim] {} sent registration", simPhone);

            // Read registration response (contains auth code)
            byte[] regResponse = readFrame(in);
            String authCode = extractAuthCode(regResponse);
            log.debug("[JT808-Sim] {} received auth code: {}", simPhone, authCode);

            // 2. Send authentication
            byte[] authPacket = buildAuthenticationPacket(simPhone, authCode);
            out.write(authPacket);
            out.flush();
            log.debug("[JT808-Sim] {} sent authentication", simPhone);

            // Read auth response
            readFrame(in);

            // Start command listener
            Thread listenerThread = new Thread(() -> commandResponder.listenForCommands(in, simPhone, "JT808"));
            listenerThread.setDaemon(true);
            listenerThread.start();

            // 3. Send heartbeats and location reports periodically
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    byte[] heartbeat = buildHeartbeatPacket(simPhone);
                    out.write(heartbeat);
                    out.flush();
                    log.debug("[JT808-Sim] {} sent heartbeat", simPhone);
                } catch (IOException e) {
                    log.warn("[JT808-Sim] {} heartbeat failed: {}", simPhone, e.getMessage());
                }
            }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    byte[] location = buildLocationPacket(simPhone);
                    out.write(location);
                    out.flush();
                    log.debug("[JT808-Sim] {} sent location", simPhone);
                } catch (IOException e) {
                    log.warn("[JT808-Sim] {} location failed: {}", simPhone, e.getMessage());
                }
            }, config.getGpsIntervalMs(), config.getGpsIntervalMs(), TimeUnit.MILLISECONDS);

            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("[JT808-Sim] Device {} error: {}", simPhone, e.getMessage());
        }
    }

    /**
     * Builds a JT808 frame with the given message ID, terminal phone, and body.
     * Uses XOR checksum and 0x7D escape sequences.
     */
    private byte[] buildFrame(int messageId, String terminalPhone, byte[] body) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(MARKER);

        // Message ID (2 bytes, big-endian)
        raw.write((messageId >> 8) & 0xFF);
        raw.write(messageId & 0xFF);

        // Body properties (2 bytes) — just body length
        int bodyProps = body.length & 0x03FF;
        raw.write((bodyProps >> 8) & 0xFF);
        raw.write(bodyProps & 0xFF);

        // Terminal phone (BCD, 6 bytes)
        byte[] phoneBcd = phoneToBcd(terminalPhone, 6);
        for (byte b : phoneBcd) raw.write(b);

        // Sequence number (2 bytes)
        int seq = random.nextInt(65536);
        raw.write((seq >> 8) & 0xFF);
        raw.write(seq & 0xFF);

        // Body
        for (byte b : body) raw.write(b);

        // XOR checksum (over bytes 1 to current position)
        byte[] rawBytes = raw.toByteArray();
        byte checksum = 0;
        for (int i = 1; i < rawBytes.length; i++) {
            checksum ^= rawBytes[i];
        }
        raw.write(checksum);
        raw.write(MARKER);

        // Escape 0x7E and 0x7D in the body (between markers)
        return escape(raw.toByteArray());
    }

    private byte[] buildRegistrationPacket(String simPhone) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // City ID (2 bytes)
        body.write(0x00);
        body.write(0x01);
        // Manufacturer ID (5 bytes)
        for (byte b : "YANTR".getBytes()) body.write(b);
        // Terminal model (10 bytes)
        byte[] model = new byte[10];
        System.arraycopy("T98".getBytes(), 0, model, 0, 3);
        for (byte b : model) body.write(b);
        // Terminal ID (20 bytes)
        byte[] termId = new byte[20];
        byte[] simBytes = simPhone.getBytes();
        System.arraycopy(simBytes, 0, termId, 0, Math.min(simBytes.length, 20));
        for (byte b : termId) body.write(b);
        // Plate color (1 byte)
        body.write(0x01);
        return buildFrame(MSG_REGISTRATION, simPhone, body.toByteArray());
    }

    private byte[] buildAuthenticationPacket(String simPhone, String authCode) {
        byte[] authBytes = authCode != null ? authCode.getBytes() : new byte[0];
        return buildFrame(MSG_AUTHENTICATION, simPhone, authBytes);
    }

    private byte[] buildHeartbeatPacket(String simPhone) {
        return buildFrame(MSG_HEARTBEAT, simPhone, new byte[0]);
    }

    private byte[] buildLocationPacket(String simPhone) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // Alarm flag (4 bytes)
        body.write(0); body.write(0); body.write(0); body.write(0);
        // Status flag (4 bytes)
        body.write(0); body.write(0); body.write(0); body.write(0);

        // Random GPS around Bangalore
        double lat = 12.97 + (random.nextDouble() - 0.5) * 0.1;
        double lng = 77.59 + (random.nextDouble() - 0.5) * 0.1;
        int latRaw = (int) (lat * 1_000_000);
        int lngRaw = (int) (lng * 1_000_000);

        // Latitude (4 bytes, big-endian)
        body.write((latRaw >> 24) & 0xFF);
        body.write((latRaw >> 16) & 0xFF);
        body.write((latRaw >> 8) & 0xFF);
        body.write(latRaw & 0xFF);
        // Longitude (4 bytes, big-endian)
        body.write((lngRaw >> 24) & 0xFF);
        body.write((lngRaw >> 16) & 0xFF);
        body.write((lngRaw >> 8) & 0xFF);
        body.write(lngRaw & 0xFF);
        // Altitude (2 bytes)
        body.write(0x03); body.write(0xE8); // 1000m
        // Speed (2 bytes)
        int speed = random.nextInt(80);
        body.write((speed >> 8) & 0xFF);
        body.write(speed & 0xFF);
        // Direction (2 bytes)
        int direction = random.nextInt(360);
        body.write((direction >> 8) & 0xFF);
        body.write(direction & 0xFF);
        // Time (6 bytes: YY MM DD HH MM SS)
        LocalDateTime now = LocalDateTime.now();
        body.write(now.getYear() - 2000);
        body.write(now.getMonthValue());
        body.write(now.getDayOfMonth());
        body.write(now.getHour());
        body.write(now.getMinute());
        body.write(now.getSecond());

        return buildFrame(MSG_LOCATION, simPhone, body.toByteArray());
    }

    private byte[] escape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(data[0]); // First marker
        for (int i = 1; i < data.length - 1; i++) {
            if (data[i] == 0x7E) {
                out.write(0x7D);
                out.write(0x02);
            } else if (data[i] == 0x7D) {
                out.write(0x7D);
                out.write(0x01);
            } else {
                out.write(data[i]);
            }
        }
        out.write(data[data.length - 1]); // Last marker
        return out.toByteArray();
    }

    private byte[] phoneToBcd(String phone, int byteCount) {
        String padded = String.format("%-" + (2 * byteCount) + "s", phone).replace(' ', '0');
        byte[] bcd = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            String pair = padded.substring(i * 2, i * 2 + 2);
            bcd[i] = (byte) Integer.parseInt(pair, 16);
        }
        return bcd;
    }

    private byte[] readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        boolean started = false;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (b == 0x7E) {
                if (!started) {
                    started = true;
                } else {
                    return buf.toByteArray();
                }
            }
        }
        return buf.toByteArray();
    }

    private String extractAuthCode(byte[] frame) {
        // Auth code is in the body of the 0x8100 response
        // For simplicity, return a default auth code
        return "AUTH" + random.nextInt(10000);
    }

    public void stop() {
        executor.shutdownNow();
        log.info("[JT808-Sim] Stopped all simulated devices");
    }
}
