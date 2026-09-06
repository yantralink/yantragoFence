package com.yantrago.gateway.tcp.jt808;

/**
 * JT808-2013 protocol constants.
 *
 * Reference: JT/T 808-2013 (China national standard for vehicle terminal communication).
 * These constants define frame markers, message IDs, and packet structure offsets.
 */
public final class JT808Constants {

    private JT808Constants() {}

    // Frame marker
    public static final byte MARKER = 0x7E;

    // Escape sequences
    public static final byte ESCAPE_MARKER = 0x7D;
    public static final byte ESCAPED_MARKER = 0x02;
    public static final byte ESCAPED_ESCAPE = 0x01;

    // Message IDs (terminal → platform)
    public static final int MSG_TERMINAL_GENERAL_RESPONSE = 0x0001;
    public static final int MSG_HEARTBEAT = 0x0002;
    public static final int MSG_LOGOUT = 0x0003;
    public static final int MSG_LOCATION_REPORT = 0x0200;
    public static final int MSG_REGISTRATION = 0x0100;
    public static final int MSG_AUTHENTICATION = 0x0102;
    public static final int MSG_MULTIMEDIA_EVENT = 0x0800;
    public static final int MSG_MULTIMEDIA_DATA = 0x0801;
    public static final int MSG_STORED_MULTIMEDIA_LIST = 0x0802;
    public static final int MSG_CAMERA_COMMAND_RESULT = 0x0805;
    public static final int MSG_AV_ATTRIBUTES_RESPONSE = 0x1003;
    public static final int MSG_STREAM_STATUS = 0x9105;

    // Message IDs (platform → terminal)
    public static final int MSG_PLATFORM_GENERAL_RESPONSE = 0x8001;
    public static final int MSG_REGISTRATION_RESPONSE = 0x8100;
    public static final int MSG_CAMERA_COMMAND = 0x8801;
    public static final int MSG_MULTIMEDIA_UPLOAD_RESPONSE = 0x8800;
    public static final int MSG_REAL_TIME_STREAM_REQUEST = 0x9101;
    public static final int MSG_STREAM_CONTROL = 0x9102;
    public static final int MSG_PTZ_COMMAND = 0x9301;
    public static final int MSG_QUERY_AV_ATTRIBUTES = 0x9003;

    // Frame structure offsets
    public static final int MSG_ID_OFFSET = 1;
    public static final int MSG_ID_LENGTH = 2;
    public static final int BODY_PROPS_OFFSET = 3;
    public static final int BODY_PROPS_LENGTH = 2;
    public static final int TERMINAL_PHONE_OFFSET = 5;
    public static final int TERMINAL_PHONE_LENGTH = 6;
    public static final int SEQUENCE_NUMBER_OFFSET = 11;
    public static final int SEQUENCE_NUMBER_LENGTH = 2;
    public static final int BODY_OFFSET = 13;

    // Body properties bit masks
    public static final int BODY_LENGTH_MASK = 0x03FF;      // 10 bits
    public static final int SUB_PACKAGE_MASK = 0x2000;      // bit 13
    public static final int ENCRYPTION_MASK = 0x1C00;       // bits 10-12

    // Terminal phone BCD length
    public static final int TERMINAL_PHONE_BCD_LENGTH = 6;

    // Default RTP port for JT1076 video streaming
    public static final int DEFAULT_RTP_PORT = 5002;

    // General response result codes
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_FAILURE = 1;
    public static final int RESULT_MSG_TYPE_NOT_SUPPORTED = 3;
}
