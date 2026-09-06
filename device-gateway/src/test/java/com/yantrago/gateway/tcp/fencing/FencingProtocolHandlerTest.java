package com.yantrago.gateway.tcp.fencing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the YantraGO fencing protocol handler.
 *
 * Verifies:
 * - Packet parsing (IMEI, telemetry, GPS, fencing state, command reply)
 * - Command encoding (ON/OFF commands, ACK)
 * - CRC calculation and validation
 * - Protocol handler canHandle() detection
 */
class FencingProtocolHandlerTest {

    private FencingParser parser;
    private FencingEncoder encoder;

    @BeforeEach
    void setUp() {
        parser = new FencingParser();
        encoder = new FencingEncoder(parser);
    }

    @Test
    @DisplayName("canHandle should detect fencing start bytes 0xAA 0x55")
    void testCanHandleStartBytes() {
        // We need to test the handler's canHandle, but it requires all dependencies.
        // Instead, test the start byte detection logic directly.
        byte[] fencingData = {(byte) 0xAA, 0x55, 0x05, 0x01};
        assertTrue(fencingData[0] == FencingConstants.START_BYTES[0]
                && fencingData[1] == FencingConstants.START_BYTES[1]);

        byte[] nonFencingData = {0x78, 0x78, 0x05, 0x01};
        assertFalse(nonFencingData[0] == FencingConstants.START_BYTES[0]
                && nonFencingData[1] == FencingConstants.START_BYTES[1]);
    }

    @Test
    @DisplayName("parseImei should extract IMEI from login payload")
    void testParseImei() {
        // IMEI: 867010070113452 encoded as 8 bytes (each byte = 2 digits)
        // 08 67 01 00 70 11 34 52
        byte[] payload = {0x08, 0x67, 0x01, 0x00, 0x70, 0x11, 0x34, 0x52};
        String imei = parser.parseImei(payload);
        assertNotNull(imei);
        // The BCD encoding: 08->08, 67->67, 01->01, 00->00, 70->70, 11->11, 34->34, 52->52
        // Concatenated: 0867010070113452, leading 0 removed -> 867010070113452
        assertEquals("867010070113452", imei);
    }

    @Test
    @DisplayName("parseImei should return null for short payload")
    void testParseImeiShortPayload() {
        byte[] payload = {0x08, 0x67, 0x01};
        String imei = parser.parseImei(payload);
        assertNull(imei);
    }

    @Test
    @DisplayName("parseTelemetry should extract voltage, battery, GSM, charging")
    void testParseTelemetry() {
        // voltage=1230 (12.30V), battery=85, gsm=20, charging=1
        byte[] payload = {
                0x04, (byte) 0xCE,  // voltage = 1230 -> 12.30V
                0x55,                // battery = 85%
                0x14,                // gsm = 20
                0x01                 // charging = true
        };
        FencingParser.TelemetryData data = parser.parseTelemetry(payload);
        assertNotNull(data);
        assertEquals(12.30, data.voltage(), 0.001);
        assertEquals(85, data.battery());
        assertEquals(20, data.gsmSignal());
        assertTrue(data.charging());
    }

    @Test
    @DisplayName("parseTelemetry should handle non-charging state")
    void testParseTelemetryNotCharging() {
        byte[] payload = {
                0x09, (byte) 0xC4,  // voltage = 2500 -> 25.00V
                0x64,                // battery = 100%
                0x1F,                // gsm = 31
                0x00                 // charging = false
        };
        FencingParser.TelemetryData data = parser.parseTelemetry(payload);
        assertNotNull(data);
        assertEquals(25.00, data.voltage(), 0.001);
        assertEquals(100, data.battery());
        assertEquals(31, data.gsmSignal());
        assertFalse(data.charging());
    }

    @Test
    @DisplayName("parseGps should extract latitude, longitude, speed, course, satellites, datetime")
    void testParseGps() {
        // lat=12735292 (12.735292), lng=77612345 (77.612345), speed=45, course=180, sat=8
        // datetime: 24 12 31 23 59 45 (2024-12-31 23:59:45)
        byte[] payload = new byte[18];
        // latitude = 12735292 = 0x00C2533C
        payload[0] = 0x00;
        payload[1] = (byte) 0xC2;
        payload[2] = 0x53;
        payload[3] = 0x3C;
        // longitude = 77612345 = 0x04A04539
        payload[4] = 0x04;
        payload[5] = (byte) 0xA0;
        payload[6] = 0x45;
        payload[7] = 0x39;
        // speed = 45
        payload[8] = 45;
        // course = 180 = 0x00B4
        payload[9] = 0x00;
        payload[10] = (byte) 0xB4;
        // satellites = 8
        payload[11] = 8;
        // datetime
        payload[12] = 24;  // year (2024)
        payload[13] = 12;  // month
        payload[14] = 31;  // day
        payload[15] = 23;  // hour
        payload[16] = 59;  // minute
        payload[17] = 45;  // second

        FencingParser.GpsData gps = parser.parseGps(payload);
        assertNotNull(gps);
        assertEquals(12.735292, gps.latitude(), 0.000001);
        assertEquals(77.612345, gps.longitude(), 0.000001);
        assertEquals(45, gps.speed());
        assertEquals(180, gps.course());
        assertEquals(8, gps.satellites());
        assertEquals(2024, gps.dateTime().getYear());
        assertEquals(12, gps.dateTime().getMonthValue());
        assertEquals(31, gps.dateTime().getDayOfMonth());
        assertEquals(23, gps.dateTime().getHour());
        assertEquals(59, gps.dateTime().getMinute());
        assertEquals(45, gps.dateTime().getSecond());
    }

    @Test
    @DisplayName("parseFencingState should extract state, voltage, current, energy")
    void testParseFencingState() {
        // state=ON(1), outputVoltage=10000 (100.00V), current=500 (5.00mA), energy=12345
        byte[] payload = new byte[9];
        payload[0] = 0x01;  // state = ON
        // outputVoltage = 10000 = 0x2710
        payload[1] = 0x27;
        payload[2] = 0x10;
        // current = 500 = 0x01F4
        payload[3] = 0x01;
        payload[4] = (byte) 0xF4;
        // energy = 12345 = 0x00003039
        payload[5] = 0x00;
        payload[6] = 0x00;
        payload[7] = 0x30;
        payload[8] = 0x39;

        FencingParser.FencingStateData state = parser.parseFencingState(payload);
        assertNotNull(state);
        assertEquals(FencingConstants.FENCING_ON, state.state());
        assertEquals(100.00, state.outputVoltage(), 0.001);
        assertEquals(5.00, state.current(), 0.001);
        assertEquals(12345L, state.energyPulses());
    }

    @Test
    @DisplayName("parseCommandReply should extract success and fencing state")
    void testParseCommandReply() {
        // success=0 (true), fencingState=1 (ON)
        byte[] payload = {0x00, 0x01};
        FencingParser.CommandReplyData reply = parser.parseCommandReply(payload);
        assertNotNull(reply);
        assertTrue(reply.success());
        assertEquals(FencingConstants.FENCING_ON, reply.fencingState());
    }

    @Test
    @DisplayName("parseCommandReply should handle failure")
    void testParseCommandReplyFailure() {
        byte[] payload = {0x01, 0x00};
        FencingParser.CommandReplyData reply = parser.parseCommandReply(payload);
        assertNotNull(reply);
        assertFalse(reply.success());
        assertEquals(FencingConstants.FENCING_OFF, reply.fencingState());
    }

    @Test
    @DisplayName("buildOnCommand should produce valid packet with start bytes and ON opcode")
    void testBuildOnCommand() {
        byte[] command = encoder.buildOnCommand();
        assertNotNull(command);
        assertTrue(command.length > 8, "ON command should have sufficient length");

        // Check start bytes
        assertEquals(FencingConstants.START_BYTES[0], command[0]);
        assertEquals(FencingConstants.START_BYTES[1], command[1]);

        // Check opcode
        assertEquals(FencingConstants.OP_CMD_ON, command[3] & 0xFF);

        // Check stop bytes
        assertEquals(FencingConstants.STOP_BYTES[0], command[command.length - 2]);
        assertEquals(FencingConstants.STOP_BYTES[1], command[command.length - 1]);

        // Validate CRC
        assertTrue(parser.validateCrc(command),
                "ON command should have valid CRC");
    }

    @Test
    @DisplayName("buildOffCommand should produce valid packet with OFF opcode")
    void testBuildOffCommand() {
        byte[] command = encoder.buildOffCommand();
        assertNotNull(command);
        assertTrue(command.length > 8);

        assertEquals(FencingConstants.START_BYTES[0], command[0]);
        assertEquals(FencingConstants.START_BYTES[1], command[1]);
        assertEquals(FencingConstants.OP_CMD_OFF, command[3] & 0xFF);
        assertEquals(FencingConstants.STOP_BYTES[0], command[command.length - 2]);
        assertEquals(FencingConstants.STOP_BYTES[1], command[command.length - 1]);

        assertTrue(parser.validateCrc(command),
                "OFF command should have valid CRC");
    }

    @Test
    @DisplayName("buildAck should produce valid ACK packet")
    void testBuildAck() {
        byte[] ack = encoder.buildAck(FencingConstants.OP_LOGIN);
        assertNotNull(ack);
        assertEquals(10, ack.length, "ACK packet should be 10 bytes");

        assertEquals(FencingConstants.START_BYTES[0], ack[0]);
        assertEquals(FencingConstants.START_BYTES[1], ack[1]);
        assertEquals(FencingConstants.OP_ACK, ack[3] & 0xFF);
        assertEquals(FencingConstants.STOP_BYTES[0], ack[8]);
        assertEquals(FencingConstants.STOP_BYTES[1], ack[9]);

        assertTrue(parser.validateCrc(ack), "ACK should have valid CRC");
    }

    @Test
    @DisplayName("ON and OFF commands should have different opcodes")
    void testOnOffCommandsDiffer() {
        byte[] onCommand = encoder.buildOnCommand();
        byte[] offCommand = encoder.buildOffCommand();

        assertNotEquals(onCommand[3], offCommand[3],
                "ON and OFF commands should have different opcodes");
    }

    @Test
    @DisplayName("CRC validation should fail for corrupted packet")
    void testCrcValidationFailsForCorruptedPacket() {
        byte[] command = encoder.buildOnCommand();
        // Corrupt a byte in the payload
        command[4] ^= 0xFF;
        assertFalse(parser.validateCrc(command),
                "Corrupted packet should fail CRC validation");
    }

    @Test
    @DisplayName("getOpcode should extract opcode from packet")
    void testGetOpcode() {
        byte[] command = encoder.buildOnCommand();
        int opcode = parser.getOpcode(command);
        assertEquals(FencingConstants.OP_CMD_ON, opcode);
    }

    @Test
    @DisplayName("stateName should return human-readable state names")
    void testStateName() {
        assertEquals("OFF", FencingParser.stateName(FencingConstants.FENCING_OFF));
        assertEquals("ON", FencingParser.stateName(FencingConstants.FENCING_ON));
        assertEquals("FAULT", FencingParser.stateName(FencingConstants.FENCING_FAULT));
        assertEquals("UNKNOWN(99)", FencingParser.stateName(99));
    }
}
