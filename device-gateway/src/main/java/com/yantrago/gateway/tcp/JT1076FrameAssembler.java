package com.yantrago.gateway.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JT1076FrameAssembler {

    private static final Logger log = LoggerFactory.getLogger(JT1076FrameAssembler.class);

    private final Map<String, ByteArrayOutputStream> frameBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> frameStartTimes = new ConcurrentHashMap<>();
    private final long timeoutMs;

    public JT1076FrameAssembler(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public byte[] assemble(JT1076RtpPacket pkt) {
        String key = pkt.getSimNumber() + ":" + pkt.getLogicalChannel();

        if (pkt.isAtomic()) {
            return pkt.getPayload();
        }

        if (pkt.isFirstPacket()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(pkt.getPayload(), 0, pkt.getPayload().length);
            frameBuffers.put(key, buf);
            frameStartTimes.put(key, System.currentTimeMillis());
            return null;
        }

        if (pkt.isIntermediatePacket()) {
            ByteArrayOutputStream buf = frameBuffers.get(key);
            if (buf == null) {
                log.warn("[RTP-Assembler] Intermediate packet without first packet: key={}", key);
                return null;
            }
            buf.write(pkt.getPayload(), 0, pkt.getPayload().length);
            return null;
        }

        if (pkt.isLastPacket()) {
            ByteArrayOutputStream buf = frameBuffers.get(key);
            if (buf == null) {
                log.warn("[RTP-Assembler] Last packet without first packet: key={}", key);
                return null;
            }
            buf.write(pkt.getPayload(), 0, pkt.getPayload().length);
            frameBuffers.remove(key);
            frameStartTimes.remove(key);
            return buf.toByteArray();
        }

        return null;
    }

    public void cleanupStaleFrames() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : frameStartTimes.entrySet()) {
            if (now - entry.getValue() > timeoutMs) {
                String key = entry.getKey();
                frameBuffers.remove(key);
                frameStartTimes.remove(key);
                log.warn("[RTP-Assembler] Dropped incomplete frame: key={}, age={}ms", key, now - entry.getValue());
            }
        }
    }
}
