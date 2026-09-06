package com.yantrago.gateway.tcp.jt808;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JT808FrameParser {

    private static final Logger log = LoggerFactory.getLogger(JT808FrameParser.class);

    public static final byte MARKER = 0x7E;

    public List<byte[]> extractFrames(byte[] data) {
        List<byte[]> frames = new ArrayList<>();
        int i = 0;
        while (i < data.length) {
            if (data[i] != MARKER) {
                i++;
                continue;
            }
            int start = i;
            int end = -1;
            for (int j = i + 1; j < data.length; j++) {
                if (data[j] == MARKER) {
                    end = j;
                    break;
                }
            }
            if (end == -1) {
                break;
            }
            int len = end - start + 1;
            byte[] frame = new byte[len];
            System.arraycopy(data, start, frame, 0, len);
            byte[] unescaped = unescape(frame);
            if (validateChecksum(unescaped)) {
                frames.add(unescaped);
            } else {
                log.warn("[JT808] Invalid checksum for frame, length={}", unescaped.length);
            }
            i = end + 1;
        }
        return frames;
    }

    public byte[] unescape(byte[] data) {
        if (data.length < 2) return data;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(data[0]);
        int i = 1;
        while (i < data.length - 1) {
            if (data[i] == 0x7D && i + 1 < data.length - 1) {
                if (data[i + 1] == 0x02) {
                    out.write(0x7E);
                    i += 2;
                } else if (data[i + 1] == 0x01) {
                    out.write(0x7D);
                    i += 2;
                } else {
                    out.write(data[i]);
                    i++;
                }
            } else {
                out.write(data[i]);
                i++;
            }
        }
        out.write(data[data.length - 1]);
        return out.toByteArray();
    }

    public byte[] escape(byte[] data) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(data[0]);
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
        out.write(data[data.length - 1]);
        return out.toByteArray();
    }

    public boolean validateChecksum(byte[] frame) {
        if (frame.length < 4) return false;
        byte calculated = calculateChecksum(frame, 1, frame.length - 2);
        byte received = frame[frame.length - 2];
        return calculated == received;
    }

    public byte calculateChecksum(byte[] data, int start, int end) {
        byte checksum = 0;
        for (int i = start; i < end; i++) {
            checksum ^= data[i];
        }
        return checksum;
    }

    public int getMessageId(byte[] frame) {
        return ((frame[1] & 0xFF) << 8) | (frame[2] & 0xFF);
    }

    public int getBodyProperties(byte[] frame) {
        return ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
    }

    public int getBodyLength(byte[] frame) {
        return getBodyProperties(frame) & 0x03FF;
    }

    public boolean isSubPackage(byte[] frame) {
        return (getBodyProperties(frame) & 0x2000) != 0;
    }

    public String getTerminalPhone(byte[] frame) {
        StringBuilder sb = new StringBuilder();
        for (int i = 5; i < 11; i++) {
            sb.append(String.format("%02X", frame[i]));
        }
        return sb.toString();
    }

    public int getSequenceNumber(byte[] frame) {
        return ((frame[11] & 0xFF) << 8) | (frame[12] & 0xFF);
    }

    public byte[] getBody(byte[] frame) {
        int bodyLen = getBodyLength(frame);
        boolean subPkg = isSubPackage(frame);
        int bodyStart = 13;
        if (subPkg) {
            bodyStart += 4;
        }
        int bodyEnd = frame.length - 2;
        if (bodyStart >= bodyEnd) return new byte[0];
        byte[] body = new byte[bodyEnd - bodyStart];
        System.arraycopy(frame, bodyStart, body, 0, body.length);
        return body;
    }
}
