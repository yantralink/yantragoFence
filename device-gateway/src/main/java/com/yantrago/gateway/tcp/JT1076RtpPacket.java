package com.yantrago.gateway.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JT1076RtpPacket {

    private static final int HEADER_MAGIC = 0x30316364;

    private int version;
    private boolean padding;
    private boolean extension;
    private int csrcCount;
    private boolean marker;
    private int payloadType;
    private int sequenceNumber;
    private String simNumber;
    private int logicalChannel;
    private int dataType;
    private int subContract;
    private long timestamp;
    private int lastIFrameInterval;
    private int lastFrameInterval;
    private int dataLength;
    private byte[] payload;

    public static JT1076RtpPacket parse(byte[] data) {
        if (data.length < 30) return null;

        ByteBuffer buf = ByteBuffer.wrap(data);

        int magic = buf.getInt();
        if (magic != HEADER_MAGIC) return null;

        int byte4 = buf.get() & 0xFF;
        JT1076RtpPacket pkt = new JT1076RtpPacket();
        pkt.version = (byte4 >> 6) & 0x03;
        pkt.padding = (byte4 & 0x20) != 0;
        pkt.extension = (byte4 & 0x10) != 0;
        pkt.csrcCount = byte4 & 0x0F;

        int byte5 = buf.get() & 0xFF;
        pkt.marker = (byte5 & 0x80) != 0;
        pkt.payloadType = byte5 & 0x7F;

        pkt.sequenceNumber = buf.getShort() & 0xFFFF;

        byte[] simBytes = new byte[6];
        buf.get(simBytes);
        pkt.simNumber = bytesToBcd(simBytes);

        pkt.logicalChannel = buf.get() & 0xFF;

        int typeByte = buf.get() & 0xFF;
        pkt.dataType = (typeByte >> 4) & 0x0F;
        pkt.subContract = typeByte & 0x0F;

        pkt.timestamp = buf.getLong();
        pkt.lastIFrameInterval = buf.getShort() & 0xFFFF;
        pkt.lastFrameInterval = buf.getShort() & 0xFFFF;
        pkt.dataLength = buf.getShort() & 0xFFFF;

        if (pkt.dataLength > 0 && buf.remaining() >= pkt.dataLength) {
            pkt.payload = new byte[pkt.dataLength];
            buf.get(pkt.payload);
        } else {
            pkt.payload = new byte[0];
        }

        return pkt;
    }

    public boolean isIFrame() { return dataType == 0; }
    public boolean isPFrame() { return dataType == 1; }
    public boolean isBFrame() { return dataType == 2; }
    public boolean isAudio() { return dataType == 3; }
    public boolean isAtomic() { return subContract == 0; }
    public boolean isFirstPacket() { return subContract == 1; }
    public boolean isLastPacket() { return subContract == 2; }
    public boolean isIntermediatePacket() { return subContract == 3; }

    private static String bytesToBcd(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public int getLogicalChannel() { return logicalChannel; }
    public String getSimNumber() { return simNumber; }
    public int getDataType() { return dataType; }
    public int getSubContract() { return subContract; }
    public long getTimestamp() { return timestamp; }
    public byte[] getPayload() { return payload; }
    public int getPayloadType() { return payloadType; }
    public int getSequenceNumber() { return sequenceNumber; }
    public boolean isMarker() { return marker; }
}
