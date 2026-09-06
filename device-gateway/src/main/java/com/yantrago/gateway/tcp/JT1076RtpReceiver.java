package com.yantrago.gateway.tcp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "jt1076.rtp.enabled", havingValue = "true", matchIfMissing = true)
public class JT1076RtpReceiver {

    private static final Logger log = LoggerFactory.getLogger(JT1076RtpReceiver.class);

    @Value("${jt1076.rtp.port:5002}")
    private int port;

    @Value("${jt1076.rtp.frame-assembly-timeout-sec:5}")
    private int frameTimeoutSec;

    @Value("${stream.relay.mediamtx-host:localhost}")
    private String mediamtxHost;

    @Value("${stream.relay.mediamtx-port:8554}")
    private int mediamtxPort;

    private DatagramSocket udpSocket;
    private ServerSocket tcpServer;
    private volatile boolean running = false;
    private JT1076FrameAssembler assembler;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void start() {
        running = true;
        assembler = new JT1076FrameAssembler(frameTimeoutSec * 1000L);

        new Thread(this::receiveUdp, "JT1076-RTP-UDP").start();
        new Thread(this::receiveTcp, "JT1076-RTP-TCP").start();

        scheduler.scheduleAtFixedRate(assembler::cleanupStaleFrames, 5, 5, TimeUnit.SECONDS);

        log.info("[JT1076] RTP receiver starting on port {} (UDP+TCP), forwarding to {}:{}",
                port, mediamtxHost, mediamtxPort);
    }

    @PreDestroy
    public void stop() {
        running = false;
        try { if (udpSocket != null) udpSocket.close(); } catch (Exception e) { /* ignore */ }
        try { if (tcpServer != null) tcpServer.close(); } catch (Exception e) { /* ignore */ }
        scheduler.shutdown();
    }

    private void receiveUdp() {
        try {
            udpSocket = new DatagramSocket(port);
            byte[] buffer = new byte[65536];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                processPacket(packet.getData(), packet.getLength(), packet.getAddress());
            }
        } catch (SocketException e) {
            if (running) log.error("[JT1076] UDP socket error", e);
        } catch (IOException e) {
            if (running) log.error("[JT1076] UDP receive error", e);
        }
    }

    private void receiveTcp() {
        try {
            tcpServer = new ServerSocket(port);
            while (running) {
                Socket client = tcpServer.accept();
                log.info("[JT1076] TCP RTP connection from {}", client.getInetAddress());
                new Thread(() -> handleTcpClient(client), "JT1076-TCP-Client").start();
            }
        } catch (IOException e) {
            if (running) log.error("[JT1076] TCP server error", e);
        }
    }

    private void handleTcpClient(Socket socket) {
        try (java.io.InputStream in = socket.getInputStream()) {
            byte[] lengthBuf = new byte[2];
            byte[] buffer = new byte[65536];

            while (running) {
                if (!readExact(in, lengthBuf, 2)) break;
                int pktLen = ((lengthBuf[0] & 0xFF) << 8) | (lengthBuf[1] & 0xFF);
                if (pktLen <= 0 || pktLen > buffer.length) break;
                if (!readExact(in, buffer, pktLen)) break;
                processPacket(buffer, pktLen, socket.getInetAddress());
            }
        } catch (IOException e) {
            log.debug("[JT1076] TCP client disconnected: {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    private boolean readExact(java.io.InputStream in, byte[] buf, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int read = in.read(buf, total, len - total);
            if (read == -1) return false;
            total += read;
        }
        return true;
    }

    private void processPacket(byte[] data, int length, InetAddress source) {
        byte[] pktData = new byte[length];
        System.arraycopy(data, 0, pktData, 0, length);

        JT1076RtpPacket pkt = JT1076RtpPacket.parse(pktData);
        if (pkt == null) {
            log.warn("[JT1076] Failed to parse RTP packet from {}, len={}", source, length);
            return;
        }

        byte[] frame = assembler.assemble(pkt);
        if (frame != null) {
            forwardToMediaMTX(frame, pkt);
        }
    }

    private void forwardToMediaMTX(byte[] nalUnit, JT1076RtpPacket pkt) {
        try (DatagramSocket forwardSocket = new DatagramSocket()) {
            byte[] rtpPacket = buildStandardRtp(nalUnit, pkt);
            InetAddress target = InetAddress.getByName(mediamtxHost);
            DatagramPacket out = new DatagramPacket(rtpPacket, rtpPacket.length, target, mediamtxPort);
            forwardSocket.send(out);
        } catch (Exception e) {
            log.error("[JT1076] Failed to forward to MediaMTX: {}", e.getMessage());
        }
    }

    private byte[] buildStandardRtp(byte[] payload, JT1076RtpPacket jtPkt) {
        byte[] rtp = new byte[12 + payload.length];
        rtp[0] = (byte) 0x80;
        rtp[1] = (byte) (jtPkt.isMarker() ? 0xE0 : 0x60);
        rtp[2] = (byte) ((jtPkt.getSequenceNumber() >> 8) & 0xFF);
        rtp[3] = (byte) (jtPkt.getSequenceNumber() & 0xFF);
        long ts = jtPkt.getTimestamp();
        for (int i = 0; i < 4; i++) {
            rtp[4 + i] = (byte) ((ts >> (24 - 8 * i)) & 0xFF);
        }
        rtp[8] = 0;
        rtp[9] = 0;
        rtp[10] = 0;
        rtp[11] = (byte) jtPkt.getLogicalChannel();
        System.arraycopy(payload, 0, rtp, 12, payload.length);
        return rtp;
    }
}
