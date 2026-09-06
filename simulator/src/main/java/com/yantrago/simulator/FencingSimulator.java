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
 * Simulates YantraGO fencing machines.
 *
 * Each simulated device:
 * 1. Connects to the gateway on the fencing TCP port (default 5002)
 * 2. Sends a login packet with its IMEI (0xAA 0x55 start bytes)
 * 3. Periodically sends heartbeat packets
 * 4. Periodically sends GPS location packets
 * 5. Periodically sends telemetry packets (voltage, battery, GSM signal)
 * 6. Periodically sends fencing state packets (ON/OFF/FAULT, output voltage, current)
 * 7. Listens for incoming command packets and responds via CommandResponder
 *
 * Uses the same CRC-16/ITU, packet structure, and start/stop bytes as the
 * gateway's FencingProtocolHandler.
 *
 * Per AGENTS.md rule 16: same protocol logic as the gateway handlers.
 */
@Component
public class FencingSimulator {

    private static final Logger log = LoggerFactory.getLogger(FencingSimulator.class);

    // Fencing protocol constants (must match FencingConstants in gateway)
    private static final byte[] START_BYTES = {(byte) 0xAA, 0x55};
    private static final byte[] STOP_BYTES = {0x0D, 0x0A};
    private static final int OP_LOGIN = 0x01;
    private static final int OP_HEARTBEAT = 0x02;
    private static final int OP_GPS = 0x03;
    private static final int OP_TELEMETRY = 0x04;
    private static final int OP_FENCING_STATE = 0x05;

    private final SimConfig config;
    private final CommandResponder commandResponder;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Random random = new Random();

    public FencingSimulator(SimConfig config, CommandResponder commandResponder) {
        this.config = config;
        this.commandResponder = commandResponder;
    }

    public void start() {
        if (!config.isFencingEnabled()) {
            log.info("[Fencing-Sim] Fencing protocol disabled, skipping");
            return;
        }

        int count = config.getDeviceCount();
        log.info("[Fencing-Sim] Starting {} fencing machines targeting {}:{}",
                count, config.getTargetHost(), config.getFencingPort());

        for (int i = 0; i < count; i++) {
            long imei = config.getStartImei() + i;
            final String imeiStr = String.valueOf(imei);
            executor.submit(() -> simulateDevice(imeiStr));
        }
    }

    private void simulateDevice(String imei) {
        try (Socket socket = new Socket(config.getTargetHost(), config.getFencingPort());
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setKeepAlive(true);
            socket.setSoTimeout(60000);
            log.info("[Fencing-Sim] Device {} connected to {}:{}", imei,
                    config.getTargetHost(), config.getFencingPort());

            // 1. Send login
            byte[] loginPacket = buildLoginPacket(imei);
            out.write(loginPacket);
            out.flush();
            log.debug("[Fencing-Sim] {} sent login", imei);

            // Read ACK
            readResponse(in);

            // Start command listener
            Thread listenerThread = new Thread(() -> commandResponder.listenForCommands(in, imei, "Fencing"));
            listenerThread.setDaemon(true);
            listenerThread.start();

            // 2. Send periodic packets
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

            // Heartbeat
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    out.write(buildHeartbeatPacket());
                    out.flush();
                    log.debug("[Fencing-Sim] {} sent heartbeat", imei);
                } catch (IOException e) {
                    log.warn("[Fencing-Sim] {} heartbeat failed: {}", imei, e.getMessage());
                }
            }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

            // GPS
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    out.write(buildGpsPacket());
                    out.flush();
                    log.debug("[Fencing-Sim] {} sent GPS", imei);
                } catch (IOException e) {
                    log.warn("[Fencing-Sim] {} GPS failed: {}", imei, e.getMessage());
                }
            }, config.getGpsIntervalMs(), config.getGpsIntervalMs(), TimeUnit.MILLISECONDS);

            // Telemetry
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    out.write(buildTelemetryPacket());
                    out.flush();
                    log.debug("[Fencing-Sim] {} sent telemetry", imei);
                } catch (IOException e) {
                    log.warn("[Fencing-Sim] {} telemetry failed: {}", imei, e.getMessage());
                }
            }, config.getTelemetryIntervalMs(), config.getTelemetryIntervalMs(), TimeUnit.MILLISECONDS);

            // Fencing state
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    out.write(buildFencingStatePacket());
                    out.flush();
                    log.debug("[Fencing-Sim] {} sent fencing state", imei);
                } catch (IOException e) {
                    log.warn("[Fencing-Sim] {} state failed: {}", imei, e.getMessage());
                }
            }, config.getTelemetryIntervalMs(), config.getTelemetryIntervalMs(), TimeUnit.MILLISECONDS);

            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("[Fencing-Sim] Device {} error: {}", imei, e.getMessage());
        }
    }

    /**
     * Builds a fencing protocol packet.
     * Structure: [START(2)] [LENGTH(1)] [OPCODE(1)] [PAYLOAD(N)] [SERIAL(2)] [CRC(2)] [STOP(2)]
     */
    private byte[] buildPacket(int opcode, byte[] payload) {
        int totalLength = 2 + 1 + 1 + payload.length + 2 + 2 + 2;
        byte[] packet = new byte[totalLength];
        int idx = 0;
        packet[idx++] = START_BYTES[0];
        packet[idx++] = START_BYTES[1];
        // Length = OPCODE(1) + PAYLOAD(N) + SERIAL(2) + CRC(2)
        packet[idx++] = (byte) (1 + payload.length + 2 + 2);
        packet[idx++] = (byte) opcode;
        for (byte b : payload) packet[idx++] = b;
        // Serial (2 bytes)
        int serial = random.nextInt(65536);
        packet[idx++] = (byte) ((serial >> 8) & 0xFF);
        packet[idx++] = (byte) (serial & 0xFF);
        // CRC (big-endian)
        int crc = Crc16Util.calculate(packet, 0, idx);
        packet[idx++] = (byte) ((crc >> 8) & 0xFF);
        packet[idx++] = (byte) (crc & 0xFF);
        // Stop
        packet[idx++] = STOP_BYTES[0];
        packet[idx] = STOP_BYTES[1];
        return packet;
    }

    private byte[] buildLoginPacket(String imei) {
        // IMEI as 8 bytes BCD
        byte[] imeiBcd = imeiToBcd(imei, 8);
        return buildPacket(OP_LOGIN, imeiBcd);
    }

    private byte[] buildHeartbeatPacket() {
        return buildPacket(OP_HEARTBEAT, new byte[0]);
    }

    private byte[] buildGpsPacket() {
        double lat = 12.97 + (random.nextDouble() - 0.5) * 0.1;
        double lng = 77.59 + (random.nextDouble() - 0.5) * 0.1;
        int speed = random.nextInt(30);
        int course = random.nextInt(360);
        int satellites = 8 + random.nextInt(4);
        LocalDateTime now = LocalDateTime.now();

        byte[] payload = new byte[18];
        int latRaw = (int) (lat * 1_000_000);
        int lngRaw = (int) (lng * 1_000_000);
        int idx = 0;
        // Latitude (4 bytes BE)
        payload[idx++] = (byte) ((latRaw >> 24) & 0xFF);
        payload[idx++] = (byte) ((latRaw >> 16) & 0xFF);
        payload[idx++] = (byte) ((latRaw >> 8) & 0xFF);
        payload[idx++] = (byte) (latRaw & 0xFF);
        // Longitude (4 bytes BE)
        payload[idx++] = (byte) ((lngRaw >> 24) & 0xFF);
        payload[idx++] = (byte) ((lngRaw >> 16) & 0xFF);
        payload[idx++] = (byte) ((lngRaw >> 8) & 0xFF);
        payload[idx++] = (byte) (lngRaw & 0xFF);
        // Speed (1 byte)
        payload[idx++] = (byte) speed;
        // Course (2 bytes BE)
        payload[idx++] = (byte) ((course >> 8) & 0xFF);
        payload[idx++] = (byte) (course & 0xFF);
        // Satellites (1 byte)
        payload[idx++] = (byte) satellites;
        // DateTime (6 bytes)
        payload[idx++] = (byte) (now.getYear() - 2000);
        payload[idx++] = (byte) now.getMonthValue();
        payload[idx++] = (byte) now.getDayOfMonth();
        payload[idx++] = (byte) now.getHour();
        payload[idx++] = (byte) now.getMinute();
        payload[idx] = (byte) now.getSecond();

        return buildPacket(OP_GPS, payload);
    }

    private byte[] buildTelemetryPacket() {
        // voltage=12.30V (1230), battery=85%, gsm=20, charging=1
        int voltageRaw = 1200 + random.nextInt(100); // 12.00V - 13.00V
        int battery = 70 + random.nextInt(30); // 70-100%
        int gsm = 15 + random.nextInt(16); // 15-31
        int charging = random.nextBoolean() ? 1 : 0;

        byte[] payload = new byte[5];
        payload[0] = (byte) ((voltageRaw >> 8) & 0xFF);
        payload[1] = (byte) (voltageRaw & 0xFF);
        payload[2] = (byte) battery;
        payload[3] = (byte) gsm;
        payload[4] = (byte) charging;

        return buildPacket(OP_TELEMETRY, payload);
    }

    private byte[] buildFencingStatePacket() {
        // state=ON(1), outputVoltage=100.00V (10000), current=5.00mA (500), energy=random
        int state = random.nextBoolean() ? 1 : 0; // ON or OFF
        int outputVoltageRaw = 8000 + random.nextInt(4000); // 80-120V
        int currentRaw = random.nextInt(1000); // 0-10mA
        long energy = random.nextInt(100000);

        byte[] payload = new byte[9];
        payload[0] = (byte) state;
        payload[1] = (byte) ((outputVoltageRaw >> 8) & 0xFF);
        payload[2] = (byte) (outputVoltageRaw & 0xFF);
        payload[3] = (byte) ((currentRaw >> 8) & 0xFF);
        payload[4] = (byte) (currentRaw & 0xFF);
        payload[5] = (byte) ((energy >> 24) & 0xFF);
        payload[6] = (byte) ((energy >> 16) & 0xFF);
        payload[7] = (byte) ((energy >> 8) & 0xFF);
        payload[8] = (byte) (energy & 0xFF);

        return buildPacket(OP_FENCING_STATE, payload);
    }

    private byte[] imeiToBcd(String imei, int byteCount) {
        String padded = String.format("%" + (2 * byteCount) + "s", imei).replace(' ', '0');
        byte[] bcd = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            String pair = padded.substring(i * 2, i * 2 + 2);
            bcd[i] = (byte) Integer.parseInt(pair, 16);
        }
        return bcd;
    }

    private void readResponse(InputStream in) throws IOException {
        byte[] buffer = new byte[256];
        int bytesRead = in.read(buffer);
        if (bytesRead > 0) {
            log.debug("[Fencing-Sim] Received response: {} bytes", bytesRead);
        }
    }

    public void stop() {
        executor.shutdownNow();
        log.info("[Fencing-Sim] Stopped all simulated devices");
    }
}
