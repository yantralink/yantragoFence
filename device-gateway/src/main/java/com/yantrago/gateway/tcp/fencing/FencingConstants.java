package com.yantrago.gateway.tcp.fencing;

/**
 * YantraGO fencing protocol constants.
 *
 * The fencing protocol is a custom binary protocol for YantraGO fencing machines.
 * Packet structure:
 *   [START(2)] [LENGTH(1)] [OPCODE(1)] [PAYLOAD(N)] [CRC(2)] [STOP(2)]
 *
 * Start bytes: 0xAA 0x55
 * Stop bytes:  0x0D 0x0A
 * CRC: CRC-16/ITU (same as Concox V5)
 */
public final class FencingConstants {

    private FencingConstants() {}

    // Start and stop bytes
    public static final byte[] START_BYTES = {(byte) 0xAA, 0x55};
    public static final byte[] STOP_BYTES = {0x0D, 0x0A};

    // Packet structure offsets
    public static final int START_OFFSET = 0;
    public static final int LENGTH_OFFSET = 2;
    public static final int OPCODE_OFFSET = 3;
    public static final int PAYLOAD_OFFSET = 4;

    // Opcodes (device → gateway)
    public static final int OP_LOGIN = 0x01;
    public static final int OP_HEARTBEAT = 0x02;
    public static final int OP_GPS = 0x03;
    public static final int OP_TELEMETRY = 0x04;
    public static final int OP_FENCING_STATE = 0x05;
    public static final int OP_COMMAND_REPLY = 0x06;
    public static final int OP_ALARM = 0x07;

    // Opcodes (gateway → device)
    public static final int OP_ACK = 0x81;
    public static final int OP_CMD_ON = 0x82;
    public static final int OP_CMD_OFF = 0x83;
    public static final int OP_CMD_RESTART = 0x84;
    public static final int OP_CMD_QUERY_STATE = 0x85;

    // Login payload
    public static final int LOGIN_IMEI_LENGTH = 8;   // 8 bytes BCD-encoded IMEI

    // Telemetry payload field offsets (relative to payload start)
    public static final int TELEMETRY_VOLTAGE_OFFSET = 0;    // 2 bytes, big-endian, value * 100 (e.g. 1230 = 12.30V)
    public static final int TELEMETRY_BATTERY_OFFSET = 2;    // 1 byte, percentage 0-100
    public static final int TELEMETRY_GSM_OFFSET = 3;        // 1 byte, signal strength 0-31
    public static final int TELEMETRY_CHARGING_OFFSET = 4;   // 1 byte, 0=not charging, 1=charging

    // GPS payload field offsets (relative to payload start)
    public static final int GPS_LAT_OFFSET = 0;       // 4 bytes, big-endian, value / 1_000_000
    public static final int GPS_LNG_OFFSET = 4;       // 4 bytes, big-endian, value / 1_000_000
    public static final int GPS_SPEED_OFFSET = 8;     // 1 byte, kph
    public static final int GPS_COURSE_OFFSET = 9;    // 2 bytes, big-endian, degrees
    public static final int GPS_SATELLITES_OFFSET = 11; // 1 byte
    public static final int GPS_DATETIME_OFFSET = 12; // 6 bytes (YY MM DD HH MM SS)

    // Fencing state payload
    public static final int FENCING_STATE_OFFSET = 0;     // 1 byte: 0=OFF, 1=ON, 2=FAULT
    public static final int FENCING_VOLTAGE_OFFSET = 1;   // 2 bytes, fence output voltage * 100
    public static final int FENCING_CURRENT_OFFSET = 3;   // 2 bytes, fence current * 100 (mA)
    public static final int FENCING_ENERGY_OFFSET = 5;    // 4 bytes, cumulative energy pulses

    // Command reply payload
    public static final int CMD_REPLY_RESULT_OFFSET = 0;  // 1 byte: 0=success, 1=failure
    public static final int CMD_REPLY_STATE_OFFSET = 1;   // 1 byte: current fencing state

    // Fencing states
    public static final int FENCING_OFF = 0;
    public static final int FENCING_ON = 1;
    public static final int FENCING_FAULT = 2;

    // CRC
    public static final int CRC_INITIAL = 0xFFFF;

    // Coordinate divisors
    public static final double COORDINATE_DIVISOR = 1_000_000.0;
    public static final double VOLTAGE_DIVISOR = 100.0;
    public static final double CURRENT_DIVISOR = 100.0;
}
