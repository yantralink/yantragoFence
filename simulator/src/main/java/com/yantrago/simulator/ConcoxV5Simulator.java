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
 * Simulates Concox V5 / BR05 GPS tracker devices.
 *
 * Each simulated device:
 * 1. Connects to the gateway on the Concox TCP port (default 5000)
 * 2. Sends a login packet with its IMEI
 * 3. Periodically sends heartbeat packets (terminal info: ACC, charging, GPS, relay)
 * 4. Periodically sends GPS location packets (lat, lng, speed, course, satellites)
 * 5. Listens for incoming command packets and responds via CommandResponder
 *
 * Uses the same CRC-16/ITU, BCD encoding, and packet structure as the gateway's
 * ConcoxV5ProtocolHandler.
 *
 * Per AGENTS.md rule 16: same protocol logic as the gateway handlers.
 */
@Component
public class ConcoxV5Simulator {

    private static final Logger log = LoggerFactory.getLogger(ConcoxV5Simulator.class);

    // Concox V5 protocol constants
    private static final byte[] START_BYTES = {0x78, 0x78};
    private static final byte[] STOP_BYTES = {0x0D, 0x0A};
    private static final int PROTO_LOGIN = 0x01;
    private static final int PROTO_HEARTBEAT = 0x13;
    private static final int PROTO_GPS = 0x22;

    private final SimConfig config;
    private final CommandResponder commandResponder;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Random random = new Random();

    public ConcoxV5Simulator(SimConfig config, CommandResponder commandResponder) {
        this.config = config;
        this.commandResponder = commandResponder;
    }

    /**
     * Starts simulating the specified number of Concox V5 devices.
     */
    public void start() {
        if (!config.isConcoxEnabled()) {
            log.info("[Concox-Sim] Concox protocol disabled, skipping");
            return;
        }

        int count = config.getDeviceCount();
        log.info("[Concox-Sim] Starting {} Concox V5 devices targeting {}:{}",
                count, config.getTargetHost(), config.getConcoxPort());

        for (int i = 0; i < count; i++) {
            long imei = config.getStartImei() + i;
            final String imeiStr = String.valueOf(imei);
            executor.submit(() -> simulateDevice(imeiStr));
        }
    }

    private void simulateDevice(String imei) {
        try (Socket socket = new Socket(config.getTargetHost(), config.getConcoxPort());
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setKeepAlive(true);
            socket.setSoTimeout(60000);
            log.info("[Concox-Sim] Device {} connected to {}:{}", imei,
                    config.getTargetHost(), config.getConcoxPort());

            // 1. Send login packet
            byte[] loginPacket = buildLoginPacket(imei);
            out.write(loginPacket);
            out.flush();
            log.debug("[Concox-Sim] {} sent login packet", imei);

            // Read login response
            readResponse(in);

            // Start command listener thread
            Thread listenerThread = new Thread(() -> commandResponder.listenForCommands(in, imei, "Concox"));
            listenerThread.setDaemon(true);
            listenerThread.start();

            // 2. Send heartbeats and GPS periodically
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    byte[] heartbeat = buildHeartbeatPacket();
                    out.write(heartbeat);
                    out.flush();
                    log.debug("[Concox-Sim] {} sent heartbeat", imei);
                } catch (IOException e) {
                    log.warn("[Concox-Sim] {} heartbeat failed: {}", imei, e.getMessage());
                }
            }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    byte[] gps = buildGpsPacket();
                    out.write(gps);
                    out.flush();
                    log.debug("[Concox-Sim] {} sent GPS", imei);
                } catch (IOException e) {
                    log.warn("[Concox-Sim] {} GPS failed: {}", imei, e.getMessage());
                }
            }, config.getGpsIntervalMs(), config.getGpsIntervalMs(), TimeUnit.MILLISECONDS);

            // Keep connection alive
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("[Concox-Sim] Device {} error: {}", imei, e.getMessage());
        }
    }

    /**
     * Builds a Concox V5 login packet.
     * Structure: [START(2)] [LENGTH(1)] [PROTO(1)] [IMEI_BCD(8)] [CRC(2)] [STOP(2)]
     */
    private byte[] buildLoginPacket(String imei) {
        byte[] imeiBcd = imeiToBcd(imei, 8);
        // Packet: START(2) + LENGTH(1) + PROTO(1) + IMEI(8) + CRC(2) + STOP(2) = 16 bytes
        // LENGTH = PROTO(1) + IMEI(8) + CRC(2) = 11
        byte[] packet = new byte[16];
        int idx = 0;
        packet[idx++] = START_BYTES[0];
        packet[idx++] = START_BYTES[1];
        packet[idx++] = 11; // length
        packet[idx++] = PROTO_LOGIN;
        for (byte b : imeiBcd) packet[idx++] = b;
        // CRC over bytes 0..idx (exclusive)
        int crc = Crc16Util.calculate(packet, 0, idx);
        packet[idx++] = (byte) (crc & 0xFF);
        packet[idx++] = (byte) ((crc >> 8) & 0xFF);
        packet[idx++] = STOP_BYTES[0];
        packet[idx] = STOP_BYTES[1];
        return packet;
    }

    /**
     * Builds a Concox V5 heartbeat packet.
     * Structure: [START(2)] [LENGTH(1)] [PROTO(1)] [TERMINAL_INFO(1)] [CRC(2)] [STOP(2)]
     */
    private byte[] buildHeartbeatPacket() {
        // LENGTH = PROTO(1) + TERMINAL_INFO(1) + CRC(2) = 4
        byte[] packet = new byte[10];
        int idx = 0;
        packet[idx++] = START_BYTES[0];
        packet[idx++] = START_BYTES[1];
        packet[idx++] = 4; // length
        packet[idx++] = PROTO_HEARTBEAT;
        // Terminal info byte: ACC=1, charging=0, GPS=1, fuelCut=0
        packet[idx++] = 0x42;
        int crc = Crc16Util.calculate(packet, 0, idx);
        packet[idx++] = (byte) (crc & 0xFF);
        packet[idx++] = (byte) ((crc >> 8) & 0xFF);
        packet[idx++] = STOP_BYTES[0];
        packet[idx] = STOP_BYTES[1];
        return packet;
    }

    /**
     * Builds a Concox V5 GPS location packet.
     * Structure: [START(2)] [LENGTH(1)] [PROTO(1)] [DATE(6)] [LAT(4)] [LNG(4)]
     *            [SPEED(1)] [COURSE_STATUS(2)] [CRC(2)] [STOP(2)]
     */
    private byte[] buildGpsPacket() {
        // Generate random GPS data around Bangalore (12.97, 77.59)
        double lat = 12.97 + (random.nextDouble() - 0.5) * 0.1;
        double lng = 77.59 + (random.nextDouble() - 0.5) * 0.1;
        int speed = random.nextInt(60);
        int course = random.nextInt(360);

        LocalDateTime now = LocalDateTime.now();
        int latRaw = (int) (lat * 1800000);
        int lngRaw = (int) (lng * 1800000);

        // LENGTH = PROTO(1) + DATE(6) + LAT(4) + LNG(4) + SPEED(1) + COURSE(2) + CRC(2) = 20
        byte[] packet = new byte[26];
        int idx = 0;
        packet[idx++] = START_BYTES[0];
        packet[idx++] = START_BYTES[1];
        packet[idx++] = 20; // length
        packet[idx++] = PROTO_GPS;
        // Date: YY MM DD HH MM SS
        packet[idx++] = (byte) (now.getYear() - 2000);
        packet[idx++] = (byte) now.getMonthValue();
        packet[idx++] = (byte) now.getDayOfMonth();
        packet[idx++] = (byte) now.getHour();
        packet[idx++] = (byte) now.getMinute();
        packet[idx++] = (byte) now.getSecond();
        // Latitude (big-endian)
        packet[idx++] = (byte) ((latRaw >> 24) & 0xFF);
        packet[idx++] = (byte) ((latRaw >> 16) & 0xFF);
        packet[idx++] = (byte) ((latRaw >> 8) & 0xFF);
        packet[idx++] = (byte) (latRaw & 0xFF);
        // Longitude (big-endian)
        packet[idx++] = (byte) ((lngRaw >> 24) & 0xFF);
        packet[idx++] = (byte) ((lngRaw >> 16) & 0xFF);
        packet[idx++] = (byte) ((lngRaw >> 8) & 0xFF);
        packet[idx++] = (byte) (lngRaw & 0xFF);
        // Speed
        packet[idx++] = (byte) speed;
        // Course/status (course in lower 10 bits, GPS located bit, N/S, E/W)
        int courseStatus = course & 0x03FF;
        courseStatus |= 0x1000; // North
        packet[idx++] = (byte) ((courseStatus >> 8) & 0xFF);
        packet[idx++] = (byte) (courseStatus & 0xFF);
        // CRC
        int crc = Crc16Util.calculate(packet, 0, idx);
        packet[idx++] = (byte) (crc & 0xFF);
        packet[idx++] = (byte) ((crc >> 8) & 0xFF);
        // Stop
        packet[idx++] = STOP_BYTES[0];
        packet[idx] = STOP_BYTES[1];
        return packet;
    }

    /**
     * Converts an IMEI string to BCD-encoded bytes (same as gateway's convertBcdToImei reverse).
     */
    private byte[] imeiToBcd(String imei, int byteCount) {
        // Pad IMEI to 2*byteCount digits, then encode each pair as BCD
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
            log.debug("[Concox-Sim] Received response: {} bytes", bytesRead);
        }
    }

    public void stop() {
        executor.shutdownNow();
        log.info("[Concox-Sim] Stopped all simulated devices");
    }
}
