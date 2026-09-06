package com.yantrago.gateway.tcp.fencing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Parses YantraGO fencing protocol packets.
 *
 * Extracts voltage, battery, GSM signal, GPS coordinates, fencing state,
 * and command reply results from packet payloads.
 *
 * Packet structure:
 *   [START(2)] [LENGTH(1)] [OPCODE(1)] [PAYLOAD(N)] [CRC(2)] [STOP(2)]
 */
@Component
public class FencingParser {

    private static final Logger log = LoggerFactory.getLogger(FencingParser.class);

    /**
     * Extracts the opcode from a packet.
     */
    public int getOpcode(byte[] packet) {
        return packet[FencingConstants.OPCODE_OFFSET] & 0xFF;
    }

    /**
     * Extracts the payload from a packet (bytes between opcode and CRC+stop).
     *
     * @param packet the full packet
     * @return payload bytes, or empty array if no payload
     */
    public byte[] getPayload(byte[] packet) {
        int payloadEnd = packet.length - 4; // Before CRC(2) + STOP(2)
        if (payloadEnd <= FencingConstants.PAYLOAD_OFFSET) {
            return new byte[0];
        }
        int len = payloadEnd - FencingConstants.PAYLOAD_OFFSET;
        byte[] payload = new byte[len];
        System.arraycopy(packet, FencingConstants.PAYLOAD_OFFSET, payload, 0, len);
        return payload;
    }

    /**
     * Parses IMEI from a login packet payload (8 bytes BCD-encoded).
     */
    public String parseImei(byte[] payload) {
        if (payload.length < FencingConstants.LOGIN_IMEI_LENGTH) {
            log.warn("[Fencing] Login payload too short: {} bytes", payload.length);
            return null;
        }
        // Each byte is BCD-encoded: high nibble = first digit, low nibble = second digit
        StringBuilder imei = new StringBuilder();
        for (int i = 0; i < FencingConstants.LOGIN_IMEI_LENGTH; i++) {
            int b = payload[i] & 0xFF;
            int high = (b >> 4) & 0x0F;
            int low = b & 0x0F;
            imei.append(high).append(low);
        }
        // Remove leading zero if present
        String result = imei.toString();
        if (result.startsWith("0")) {
            result = result.substring(1);
        }
        return result;
    }

    /**
     * Parses telemetry from a telemetry packet payload.
     *
     * @return TelemetryData with voltage, battery, gsmSignal, charging
     */
    public TelemetryData parseTelemetry(byte[] payload) {
        if (payload.length < 5) {
            log.warn("[Fencing] Telemetry payload too short: {} bytes", payload.length);
            return null;
        }
        double voltage = readUInt16(payload, FencingConstants.TELEMETRY_VOLTAGE_OFFSET)
                / FencingConstants.VOLTAGE_DIVISOR;
        int battery = payload[FencingConstants.TELEMETRY_BATTERY_OFFSET] & 0xFF;
        int gsmSignal = payload[FencingConstants.TELEMETRY_GSM_OFFSET] & 0xFF;
        boolean charging = (payload[FencingConstants.TELEMETRY_CHARGING_OFFSET] & 0xFF) == 1;

        log.debug("[Fencing] Telemetry: voltage={}V battery={}% gsm={} charging={}",
                voltage, battery, gsmSignal, charging);

        return new TelemetryData(voltage, battery, gsmSignal, charging);
    }

    /**
     * Parses GPS data from a GPS packet payload.
     *
     * @return GpsData with latitude, longitude, speed, course, satellites, dateTime
     */
    public GpsData parseGps(byte[] payload) {
        if (payload.length < 18) {
            log.warn("[Fencing] GPS payload too short: {} bytes", payload.length);
            return null;
        }
        double latitude = readUInt32(payload, FencingConstants.GPS_LAT_OFFSET)
                / FencingConstants.COORDINATE_DIVISOR;
        double longitude = readUInt32(payload, FencingConstants.GPS_LNG_OFFSET)
                / FencingConstants.COORDINATE_DIVISOR;
        int speed = payload[FencingConstants.GPS_SPEED_OFFSET] & 0xFF;
        int course = readUInt16(payload, FencingConstants.GPS_COURSE_OFFSET);
        int satellites = payload[FencingConstants.GPS_SATELLITES_OFFSET] & 0xFF;

        int year = payload[FencingConstants.GPS_DATETIME_OFFSET] & 0xFF;
        int month = payload[FencingConstants.GPS_DATETIME_OFFSET + 1] & 0xFF;
        int day = payload[FencingConstants.GPS_DATETIME_OFFSET + 2] & 0xFF;
        int hour = payload[FencingConstants.GPS_DATETIME_OFFSET + 3] & 0xFF;
        int minute = payload[FencingConstants.GPS_DATETIME_OFFSET + 4] & 0xFF;
        int second = payload[FencingConstants.GPS_DATETIME_OFFSET + 5] & 0xFF;

        LocalDateTime dateTime = LocalDateTime.of(2000 + year, month, day, hour, minute, second);

        log.debug("[Fencing] GPS: lat={} lng={} speed={}kph course={}° sat={}",
                latitude, longitude, speed, course, satellites);

        return new GpsData(latitude, longitude, speed, course, satellites, dateTime);
    }

    /**
     * Parses fencing state from a fencing state packet payload.
     *
     * @return FencingStateData with state, outputVoltage, current, energyPulses
     */
    public FencingStateData parseFencingState(byte[] payload) {
        if (payload.length < 9) {
            log.warn("[Fencing] State payload too short: {} bytes", payload.length);
            return null;
        }
        int state = payload[FencingConstants.FENCING_STATE_OFFSET] & 0xFF;
        double outputVoltage = readUInt16(payload, FencingConstants.FENCING_VOLTAGE_OFFSET)
                / FencingConstants.VOLTAGE_DIVISOR;
        double current = readUInt16(payload, FencingConstants.FENCING_CURRENT_OFFSET)
                / FencingConstants.CURRENT_DIVISOR;
        long energyPulses = readUInt32(payload, FencingConstants.FENCING_ENERGY_OFFSET);

        log.debug("[Fencing] State: state={} outputV={}V current={}mA energy={}",
                stateName(state), outputVoltage, current, energyPulses);

        return new FencingStateData(state, outputVoltage, current, energyPulses);
    }

    /**
     * Parses command reply from a command reply packet payload.
     *
     * @return CommandReplyData with success and currentFencingState
     */
    public CommandReplyData parseCommandReply(byte[] payload) {
        if (payload.length < 2) {
            log.warn("[Fencing] Command reply payload too short: {} bytes", payload.length);
            return null;
        }
        boolean success = (payload[FencingConstants.CMD_REPLY_RESULT_OFFSET] & 0xFF) == 0;
        int fencingState = payload[FencingConstants.CMD_REPLY_STATE_OFFSET] & 0xFF;

        log.debug("[Fencing] Command reply: success={} fencingState={}", success, stateName(fencingState));

        return new CommandReplyData(success, fencingState);
    }

    /**
     * Validates the CRC-16 of a packet.
     */
    public boolean validateCrc(byte[] packet) {
        if (packet.length < 6) return false; // Minimum: start(2) + length(1) + opcode(1) + crc(2) ... need stop too
        int crcEnd = packet.length - 4; // Before CRC(2) + STOP(2)
        int calculated = calculateCrc16(packet, 0, crcEnd);
        int received = ((packet[crcEnd] & 0xFF) << 8) | (packet[crcEnd + 1] & 0xFF);
        return calculated == received;
    }

    /**
     * Calculates CRC-16/ITU (same algorithm as Concox V5).
     */
    public int calculateCrc16(byte[] data, int start, int end) {
        int fcs = FencingConstants.CRC_INITIAL;
        for (int i = start; i < end; i++) {
            fcs = (fcs >> 8) ^ CRC16_TABLE[(fcs ^ (data[i] & 0xFF)) & 0xFF];
        }
        return ~fcs & 0xFFFF;
    }

    // ===== Helper methods =====

    private int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private long readUInt32(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF) << 24)
                | ((long)(data[offset + 1] & 0xFF) << 16)
                | ((long)(data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    public static String stateName(int state) {
        return switch (state) {
            case FencingConstants.FENCING_OFF -> "OFF";
            case FencingConstants.FENCING_ON -> "ON";
            case FencingConstants.FENCING_FAULT -> "FAULT";
            default -> "UNKNOWN(" + state + ")";
        };
    }

    // ===== Data classes =====

    public record TelemetryData(double voltage, int battery, int gsmSignal, boolean charging) {}

    public record GpsData(double latitude, double longitude, int speed, int course,
                          int satellites, LocalDateTime dateTime) {}

    public record FencingStateData(int state, double outputVoltage, double current, long energyPulses) {}

    public record CommandReplyData(boolean success, int fencingState) {}

    // CRC-ITU lookup table (same as Concox V5)
    private static final int[] CRC16_TABLE = {
        0x0000, 0x1189, 0x2312, 0x329B, 0x4624, 0x57AD, 0x6536, 0x74BF,
        0x8C48, 0x9DC1, 0xAF5A, 0xBED3, 0xCA6C, 0xDBE5, 0xE97E, 0xF8F7,
        0x1081, 0x0108, 0x3393, 0x221A, 0x56A5, 0x472C, 0x75B7, 0x643E,
        0x9CC9, 0x8D40, 0xBFDB, 0xAE52, 0xDAED, 0xCB64, 0xF9FF, 0xE876,
        0x2102, 0x308B, 0x0210, 0x1399, 0x6726, 0x76AF, 0x4434, 0x55BD,
        0xAD4A, 0xBCC3, 0x8E58, 0x9FD1, 0xEB6E, 0xFAE7, 0xC87C, 0xD9F5,
        0x3183, 0x200A, 0x1291, 0x0318, 0x77A7, 0x662E, 0x54B5, 0x453C,
        0xBDCB, 0xAC42, 0x9ED9, 0x8F50, 0xFBEF, 0xEA66, 0xD8FD, 0xC974,
        0x4204, 0x538D, 0x6116, 0x709F, 0x0420, 0x15A9, 0x2732, 0x36BB,
        0xCE4C, 0xDFC5, 0xED5E, 0xFCD7, 0x8868, 0x99E1, 0xAB7A, 0xBAF3,
        0x5285, 0x430C, 0x7197, 0x601E, 0x14A1, 0x0528, 0x37B3, 0x263A,
        0xDECD, 0xCF44, 0xFDDF, 0xEC56, 0x98E9, 0x8960, 0xBBFB, 0xAA72,
        0x6306, 0x728F, 0x4014, 0x519D, 0x2522, 0x34AB, 0x0630, 0x17B9,
        0xEF4E, 0xFEC7, 0xCC5C, 0xDDD5, 0xA96A, 0xB8E3, 0x8A78, 0x9BF1,
        0x7387, 0x620E, 0x5095, 0x411C, 0x35A3, 0x242A, 0x16B1, 0x0738,
        0xFFCF, 0xEE46, 0xDCDD, 0xCD54, 0xB9EB, 0xA862, 0x9AF9, 0x8B70,
        0x8408, 0x9581, 0xA71A, 0xB693, 0xC22C, 0xD3A5, 0xE13E, 0xF0B7,
        0x0840, 0x19C9, 0x2B52, 0x3ADB, 0x4E64, 0x5FED, 0x6D76, 0x7CFF,
        0x9489, 0x8500, 0xB79B, 0xA612, 0xD2AD, 0xC324, 0xF1BF, 0xE036,
        0x18C1, 0x0948, 0x3BD3, 0x2A5A, 0x5EE5, 0x4F6C, 0x7DF7, 0x6C7E,
        0xA50A, 0xB483, 0x8618, 0x9791, 0xE32E, 0xF2A7, 0xC03C, 0xD1B5,
        0x2942, 0x38CB, 0x0A50, 0x1BD9, 0x6F66, 0x7EEF, 0x4C74, 0x5DFD,
        0xB58B, 0xA402, 0x9699, 0x8710, 0xF3AF, 0xE226, 0xD0BD, 0xC134,
        0x39C3, 0x284A, 0x1AD1, 0x0B58, 0x7FE7, 0x6E6E, 0x5CF5, 0x4D7C,
        0xC60C, 0xD785, 0xE51E, 0xF497, 0x8028, 0x91A1, 0xA33A, 0xB2B3,
        0x4A44, 0x5BCD, 0x6956, 0x78DF, 0x0C60, 0x1DE9, 0x2F72, 0x3EFB,
        0xD68D, 0xC704, 0xF59F, 0xE416, 0x90A9, 0x8120, 0xB3BB, 0xA232,
        0x5AC5, 0x4B4C, 0x79D7, 0x685E, 0x1CE1, 0x0D68, 0x3FF3, 0x2E7A,
        0xE70E, 0xF687, 0xC41C, 0xD595, 0xA12A, 0xB0A3, 0x8238, 0x93B1,
        0x6B46, 0x7ACF, 0x4854, 0x59DD, 0x2D62, 0x3CEB, 0x0E70, 0x1FF9,
        0xF78F, 0xE606, 0xD49D, 0xC514, 0xB1AB, 0xA022, 0x92B9, 0x8330,
        0x7BC7, 0x6A4E, 0x58D5, 0x495C, 0x3DE3, 0x2C6A, 0x1EF1, 0x0F78
    };
}
