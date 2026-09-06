package com.yantrago.gateway.tcp.jt808;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class JT808MessageEncoder {

    private static final Logger log = LoggerFactory.getLogger(JT808MessageEncoder.class);

    private static final byte MARKER = 0x7E;
    private static final int DEFAULT_RTP_PORT = 5002;

    private final JT808FrameParser frameParser;
    private volatile int sequenceNumber = 1;

    public JT808MessageEncoder(JT808FrameParser frameParser) {
        this.frameParser = frameParser;
    }

    private synchronized int nextSeq() {
        return sequenceNumber++;
    }

    public byte[] buildFrame(int messageId, String terminalPhone, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);

        byte[] msgIdBytes = intToBytes(messageId, 2);
        out.write(msgIdBytes[0]);
        out.write(msgIdBytes[1]);

        int bodyProps = body.length & 0x03FF;
        byte[] bodyPropsBytes = intToBytes(bodyProps, 2);
        out.write(bodyPropsBytes[0]);
        out.write(bodyPropsBytes[1]);

        byte[] phoneBytes = parseBcdPhone(terminalPhone);
        for (byte b : phoneBytes) out.write(b);

        int seq = nextSeq();
        byte[] seqBytes = intToBytes(seq, 2);
        out.write(seqBytes[0]);
        out.write(seqBytes[1]);

        for (byte b : body) out.write(b);

        byte[] beforeChecksum = out.toByteArray();
        byte checksum = frameParser.calculateChecksum(beforeChecksum, 1, beforeChecksum.length);
        out.write(checksum);
        out.write(MARKER);

        return frameParser.escape(out.toByteArray());
    }

    public byte[] buildGeneralResponse(String terminalPhone, int responseSeq, int responseMsgId, int result) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] seqBytes = intToBytes(responseSeq, 2);
        body.write(seqBytes[0]);
        body.write(seqBytes[1]);
        byte[] msgIdBytes = intToBytes(responseMsgId, 2);
        body.write(msgIdBytes[0]);
        body.write(msgIdBytes[1]);
        body.write(result & 0xFF);
        return buildFrame(0x8001, terminalPhone, body.toByteArray());
    }

    public byte[] buildRegistrationResponse(String terminalPhone, int responseSeq, String authCode, int result) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] seqBytes = intToBytes(responseSeq, 2);
        body.write(seqBytes[0]);
        body.write(seqBytes[1]);
        byte[] authBytes = authCode.getBytes();
        body.write(authBytes.length & 0xFF);
        for (byte b : authBytes) body.write(b);
        body.write(result & 0xFF);
        return buildFrame(0x8100, terminalPhone, body.toByteArray());
    }

    public byte[] buildRealTimeStreamRequest(String terminalPhone, int channel, int dataType, int streamType, int rtpPort) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] ipBytes = {65, 20, 81, 89};
        for (byte b : ipBytes) body.write(b);

        byte[] tcpPortBytes = intToBytes(rtpPort, 2);
        body.write(tcpPortBytes[0]);
        body.write(tcpPortBytes[1]);

        byte[] udpPortBytes = intToBytes(rtpPort, 2);
        body.write(udpPortBytes[0]);
        body.write(udpPortBytes[1]);

        body.write(channel & 0xFF);
        body.write(dataType & 0xFF);
        body.write(streamType & 0xFF);

        return buildFrame(0x9101, terminalPhone, body.toByteArray());
    }

    public byte[] buildStreamControl(String terminalPhone, int channel, int controlCommand) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(channel & 0xFF);
        body.write(controlCommand & 0xFF);
        body.write(0);
        return buildFrame(0x9102, terminalPhone, body.toByteArray());
    }

    public byte[] buildCameraCommand(String terminalPhone, int channel, int command, int time, int resolution) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(channel & 0xFF);
        byte[] cmdBytes = intToBytes(command, 2);
        body.write(cmdBytes[0]);
        body.write(cmdBytes[1]);
        body.write(time & 0xFF);
        body.write(0);
        body.write(resolution & 0xFF);
        body.write(0);
        body.write(0);
        body.write(0);
        return buildFrame(0x8801, terminalPhone, body.toByteArray());
    }

    public byte[] buildPtzCommand(String terminalPhone, int channel, int direction, int speed) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(channel & 0xFF);
        body.write(direction & 0xFF);
        body.write(speed & 0xFF);
        return buildFrame(0x9301, terminalPhone, body.toByteArray());
    }

    public byte[] buildMultimediaUploadResponse(String terminalPhone, int multimediaId, int result) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] mmIdBytes = intToBytes(multimediaId, 4);
        for (byte b : mmIdBytes) body.write(b);
        body.write(result & 0xFF);
        return buildFrame(0x8800, terminalPhone, body.toByteArray());
    }

    public byte[] buildQueryAvAttributes(String terminalPhone) {
        return buildFrame(0x9003, terminalPhone, new byte[0]);
    }

    private byte[] intToBytes(int value, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[length - 1 - i] = (byte) ((value >> (8 * i)) & 0xFF);
        }
        return bytes;
    }

    private byte[] parseBcdPhone(String phone) {
        byte[] result = new byte[6];
        String padded = String.format("%-12s", phone).replace(' ', '0');
        for (int i = 0; i < 6; i++) {
            String pair = padded.substring(i * 2, i * 2 + 2);
            result[i] = (byte) Integer.parseInt(pair, 16);
        }
        return result;
    }
}
