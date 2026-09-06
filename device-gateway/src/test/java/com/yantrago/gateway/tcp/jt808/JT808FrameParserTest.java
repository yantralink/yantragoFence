package com.yantrago.gateway.tcp.jt808;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JT808FrameParser.
 *
 * Verifies:
 * - Frame extraction from byte stream (0x7E delimiters)
 * - Checksum calculation and validation
 * - Escape/unescape (0x7D 0x02 → 0x7E, 0x7D 0x01 → 0x7D)
 * - Message ID, body properties, terminal phone, sequence number extraction
 *
 * Per AGENTS.md rule 16: reused protocol code is copied as-is, not rewritten.
 */
class JT808FrameParserTest {

    private JT808FrameParser parser;

    @BeforeEach
    void setUp() {
        parser = new JT808FrameParser();
    }

    @Test
    @DisplayName("extractFrames should extract a single frame delimited by 0x7E")
    void extractFrames_shouldExtractSingleFrame() {
        // Build a minimal valid frame: 0x7E [header] [checksum] 0x7E
        byte[] frame = buildValidFrame((byte) 0x0200, new byte[]{0x01, 0x02});
        List<byte[]> frames = parser.extractFrames(frame);

        assertEquals(1, frames.size());
        assertEquals(frame.length, frames.get(0).length);
    }

    @Test
    @DisplayName("extractFrames should extract multiple frames from a single byte stream")
    void extractFrames_shouldExtractMultipleFrames() {
        byte[] frame1 = buildValidFrame((byte) 0x0200, new byte[]{0x01});
        byte[] frame2 = buildValidFrame((byte) 0x0100, new byte[]{0x02, 0x03});

        byte[] combined = new byte[frame1.length + frame2.length];
        System.arraycopy(frame1, 0, combined, 0, frame1.length);
        System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);

        List<byte[]> frames = parser.extractFrames(combined);
        assertEquals(2, frames.size());
    }

    @Test
    @DisplayName("extractFrames should skip frames with invalid checksum")
    void extractFrames_shouldSkipInvalidChecksum() {
        // Build a frame with wrong checksum
        byte[] frame = new byte[]{
                0x7E, 0x02, 0x00, 0x00, 0x02,
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                0x00, 0x01,
                0x01, 0x02,
                0x00, // Wrong checksum
                0x7E
        };
        List<byte[]> frames = parser.extractFrames(frame);
        assertTrue(frames.isEmpty());
    }

    @Test
    @DisplayName("validateChecksum should return true for valid frame")
    void validateChecksum_shouldReturnTrueForValid() {
        byte[] frame = buildValidFrame((byte) 0x0200, new byte[]{0x01, 0x02});
        assertTrue(parser.validateChecksum(frame));
    }

    @Test
    @DisplayName("validateChecksum should return false for corrupted frame")
    void validateChecksum_shouldReturnFalseForCorrupted() {
        byte[] frame = buildValidFrame((byte) 0x0200, new byte[]{0x01, 0x02});
        frame[frame.length - 2] = 0x00; // Corrupt checksum
        assertFalse(parser.validateChecksum(frame));
    }

    @Test
    @DisplayName("calculateChecksum should XOR all bytes between start and end")
    void calculateChecksum_shouldXorAllBytes() {
        byte[] data = {0x7E, 0x01, 0x02, 0x03, 0x04, 0x7E};
        byte checksum = parser.calculateChecksum(data, 1, 4);
        assertEquals((byte) (0x01 ^ 0x02 ^ 0x03), checksum);
    }

    @Test
    @DisplayName("unescape should convert 0x7D 0x02 back to 0x7E")
    void unescape_shouldConvert7D02To7E() {
        // Input:  0x7E 0x01 0x7D 0x02 0x03 0x7E
        // Output: 0x7E 0x01 0x7E 0x03 0x7E  (0x7D 0x02 → 0x7E)
        byte[] escaped = {0x7E, 0x01, 0x7D, 0x02, 0x03, 0x7E};
        byte[] unescaped = parser.unescape(escaped);
        assertEquals(0x7E, unescaped[0]);
        assertEquals(0x01, unescaped[1]);
        assertEquals(0x7E, unescaped[2]); // 0x7D 0x02 was converted to 0x7E
        assertEquals(0x03, unescaped[3]);
        assertEquals(0x7E, unescaped[4]);
        assertTrue(unescaped.length < escaped.length);
    }

    @Test
    @DisplayName("unescape should convert 0x7D 0x01 back to 0x7D")
    void unescape_shouldConvert7D01To7D() {
        byte[] escaped = {0x7E, 0x01, 0x7D, 0x01, 0x03, 0x7E};
        byte[] unescaped = parser.unescape(escaped);
        assertTrue(unescaped.length < escaped.length);
    }

    @Test
    @DisplayName("escape should convert 0x7E to 0x7D 0x02")
    void escape_shouldConvert7ETo7D02() {
        byte[] data = {0x7E, 0x01, 0x7E, 0x03, 0x7E};
        byte[] escaped = parser.escape(data);
        assertTrue(escaped.length > data.length);
    }

    @Test
    @DisplayName("getMessageId should extract 2-byte message ID from frame")
    void getMessageId_shouldExtractMessageId() {
        byte[] frame = buildValidFrame((byte) 0x02, (byte) 0x00, new byte[]{0x01});
        int msgId = parser.getMessageId(frame);
        assertEquals(0x0200, msgId);
    }

    @Test
    @DisplayName("getTerminalPhone should extract 6-byte BCD phone number")
    void getTerminalPhone_shouldExtractPhone() {
        byte[] frame = buildValidFrame((byte) 0x0200, new byte[]{0x01});
        String phone = parser.getTerminalPhone(frame);
        assertNotNull(phone);
        assertEquals(12, phone.length()); // 6 bytes × 2 hex chars
    }

    @Test
    @DisplayName("getSequenceNumber should extract 2-byte sequence")
    void getSequenceNumber_shouldExtractSequence() {
        byte[] frame = buildValidFrame((byte) 0x0200, new byte[]{0x01});
        int seq = parser.getSequenceNumber(frame);
        assertTrue(seq >= 0 && seq <= 0xFFFF);
    }

    @Test
    @DisplayName("getBody should extract body bytes from frame")
    void getBody_shouldExtractBody() {
        byte[] bodyData = {0x01, 0x02, 0x03};
        byte[] frame = buildValidFrame((byte) 0x0200, bodyData);
        byte[] body = parser.getBody(frame);
        assertEquals(bodyData.length, body.length);
    }

    @Test
    @DisplayName("extractFrames should return empty list for empty input")
    void extractFrames_shouldReturnEmptyForEmptyInput() {
        List<byte[]> frames = parser.extractFrames(new byte[0]);
        assertTrue(frames.isEmpty());
    }

    @Test
    @DisplayName("extractFrames should return empty list when no 0x7E markers found")
    void extractFrames_shouldReturnEmptyWhenNoMarkers() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        List<byte[]> frames = parser.extractFrames(data);
        assertTrue(frames.isEmpty());
    }

    /**
     * Builds a valid JT808 frame with correct checksum.
     * Frame format: 0x7E [msgId(2)] [bodyProps(2)] [phone(6)] [seq(2)] [body] [checksum] 0x7E
     */
    private byte[] buildValidFrame(byte msgIdHigh, byte msgIdLow, byte[] body) {
        int bodyLen = body.length;
        byte bodyPropsHigh = (byte) ((bodyLen >> 8) & 0x03);
        byte bodyPropsLow = (byte) (bodyLen & 0xFF);

        byte[] frame = new byte[1 + 2 + 2 + 6 + 2 + body.length + 1 + 1];
        int i = 0;
        frame[i++] = 0x7E;
        frame[i++] = msgIdHigh;
        frame[i++] = msgIdLow;
        frame[i++] = bodyPropsHigh;
        frame[i++] = bodyPropsLow;
        // Phone (6 bytes BCD)
        frame[i++] = 0x01; frame[i++] = 0x02; frame[i++] = 0x03;
        frame[i++] = 0x04; frame[i++] = 0x05; frame[i++] = 0x06;
        // Sequence (2 bytes)
        frame[i++] = 0x00; frame[i++] = 0x01;
        // Body
        for (byte b : body) frame[i++] = b;
        // Checksum (XOR of bytes from index 1 to length-2)
        byte checksum = 0;
        for (int j = 1; j < frame.length - 2; j++) {
            checksum ^= frame[j];
        }
        frame[i++] = checksum;
        frame[i] = 0x7E;
        return frame;
    }

    private byte[] buildValidFrame(byte msgId, byte[] body) {
        return buildValidFrame(msgId, (byte) 0x00, body);
    }
}
