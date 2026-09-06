package com.yantrago.gateway.tcp.concox;

/**
 * Concox V5 / BR05 protocol constants.
 *
 * Reference: Concox V5 protocol specification (BR05).
 * These constants define start bits, protocol numbers, and packet structure offsets.
 */
public final class ConcoxV5Constants {

    private ConcoxV5Constants() {}

    // Start bits
    public static final byte[] START_BITS_STANDARD = {0x78, 0x78};
    public static final byte[] START_BITS_EXTENDED = {0x79, 0x79};

    // Stop bits
    public static final byte[] STOP_BITS = {0x0D, 0x0A};

    // Protocol numbers
    public static final int PROTO_LOGIN = 0x01;
    public static final int PROTO_HEARTBEAT = 0x13;
    public static final int PROTO_COMMAND_REPLY = 0x21;
    public static final int PROTO_GPS_LOCATION = 0x22;
    public static final int PROTO_ALARM = 0x26;
    public static final int PROTO_INFO_TRANSMISSION = 0x94;
    public static final int PROTO_COMMAND = 0x80;

    // Terminal info bit masks (heartbeat / command reply)
    public static final int BIT_ACC = 0x02;
    public static final int BIT_CHARGING = 0x04;
    public static final int BIT_GPS_TRACKING = 0x40;
    public static final int BIT_FUEL_CUTOFF = 0x80;
    public static final int BIT_SOS = 0x01;
    public static final int BIT_VIBRATION = 0x08;

    // Packet structure offsets (standard / extended)
    public static final int STANDARD_PROTOCOL_OFFSET = 3;
    public static final int EXTENDED_PROTOCOL_OFFSET = 4;
    public static final int STANDARD_IMEI_OFFSET = 4;
    public static final int EXTENDED_IMEI_OFFSET = 5;
    public static final int STANDARD_INFO_OFFSET = 4;
    public static final int EXTENDED_INFO_OFFSET = 5;

    // IMEI
    public static final int IMEI_BYTES = 8;

    // GPS location packet field sizes
    public static final int GPS_DATETIME_BYTES = 6;
    public static final int GPS_LAT_BYTES = 4;
    public static final int GPS_LNG_BYTES = 4;
    public static final int GPS_SPEED_BYTES = 1;
    public static final int GPS_COURSE_STATUS_BYTES = 2;

    // Course/status bit masks
    public static final int COURSE_MASK = 0x03FF;       // 10 bits
    public static final int GPS_LOCATED_MASK = 0x0400;   // bit 10
    public static final int EAST_WEST_MASK = 0x0800;     // bit 11 (0=East, 1=West)
    public static final int NORTH_SOUTH_MASK = 0x1000;   // bit 12 (1=North, 0=South)

    // Coordinate divisors
    public static final double LAT_LNG_DIVISOR = 1800000.0;

    // CRC
    public static final int CRC_INITIAL = 0xFFFF;
}
