package com.yantrago.gateway.tcp.jt808;

import com.yantrago.gateway.model.Dashcam;
import com.yantrago.gateway.repository.DashcamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class JT808ProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(JT808ProtocolHandler.class);

    private final Map<String, String> clientSimMap = new ConcurrentHashMap<>();
    private final JT808FrameParser frameParser;
    private final JT808MessageEncoder encoder;
    private final DashcamRepository dashcamRepository;
    private final AtomicInteger authCodeCounter = new AtomicInteger(1000);

    public JT808ProtocolHandler(JT808FrameParser frameParser,
                                 JT808MessageEncoder encoder,
                                 DashcamRepository dashcamRepository) {
        this.frameParser = frameParser;
        this.encoder = encoder;
        this.dashcamRepository = dashcamRepository;
    }

    public String getSimPhoneForClient(String clientId) {
        return clientSimMap.get(clientId);
    }

    public void removeClient(String clientId) {
        String sim = clientSimMap.remove(clientId);
        if (sim != null) {
            dashcamRepository.markOffline(sim);
            log.info("[JT808] Marked dashcam offline: sim={}", sim);
        }
    }

    public byte[] handleFrame(byte[] frame, String clientId, OutputStream out) {
        if (frame.length < 13) return null;

        int msgId = frameParser.getMessageId(frame);
        String simPhone = frameParser.getTerminalPhone(frame);
        int seq = frameParser.getSequenceNumber(frame);
        byte[] body = frameParser.getBody(frame);

        log.info("[JT808] MsgId=0x{}, sim={}, seq={}, bodyLen={}",
                String.format("%04X", msgId), simPhone, seq, body.length);

        clientSimMap.put(clientId, simPhone);

        return switch (msgId) {
            case 0x0100 -> handleRegistration(frame, simPhone, seq, body);
            case 0x0102 -> handleAuthentication(frame, simPhone, seq, body);
            case 0x0002 -> handleHeartbeat(simPhone, seq);
            case 0x0003 -> handleLogout(simPhone, seq, clientId);
            case 0x0200 -> handleLocationReport(simPhone, seq, body);
            case 0x0800 -> handleMultimediaEvent(simPhone, seq, body);
            case 0x0801 -> handleMultimediaData(simPhone, seq, body, out);
            case 0x0802 -> handleStoredMultimediaList(simPhone, seq, body);
            case 0x0805 -> handleCameraCommandResult(simPhone, seq, body);
            case 0x1003 -> handleAvAttributesResponse(simPhone, seq, body);
            case 0x0001 -> handleTerminalGeneralResponse(simPhone, seq, body);
            case 0x9105 -> handleStreamStatus(simPhone, seq, body);
            default -> {
                log.warn("[JT808] Unhandled MsgId=0x{}", String.format("%04X", msgId));
                yield encoder.buildGeneralResponse(simPhone, seq, msgId, 3);
            }
        };
    }

    private byte[] handleRegistration(byte[] frame, String simPhone, int seq, byte[] body) {
        if (body.length < 37) {
            log.warn("[JT808] Registration body too short: {} bytes", body.length);
            return encoder.buildRegistrationResponse(simPhone, seq, "", 1);
        }

        int cityId = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        StringBuilder manufacturerId = new StringBuilder();
        for (int i = 2; i < 7; i++) manufacturerId.append((char)(body[i] & 0xFF));
        StringBuilder terminalModel = new StringBuilder();
        for (int i = 7; i < 17; i++) terminalModel.append((char)(body[i] & 0xFF));
        StringBuilder terminalId = new StringBuilder();
        for (int i = 17; i < 37; i++) terminalId.append((char)(body[i] & 0xFF));

        String imeiRaw = terminalId.toString().replaceAll("[^0-9]", "");
        final String imei = imeiRaw.length() > 15 ? imeiRaw.substring(0, 15) : imeiRaw;

        int plateColor = body.length > 37 ? body[37] & 0xFF : 0;

        log.info("[JT808] Registration: sim={}, manufacturer={}, model={}, terminalId={}, imei={}",
                simPhone, manufacturerId, terminalModel, terminalId, imei);

        String authCode = "AUTH" + authCodeCounter.getAndIncrement();

        Dashcam dashcam = dashcamRepository.findBySimPhone(simPhone)
                .orElseGet(() -> {
                    Dashcam d = new Dashcam();
                    d.setSimPhone(simPhone);
                    d.setImei(imei);
                    d.setManufacturerId(manufacturerId.toString());
                    d.setModel(terminalModel.toString());
                    d.setStatus(Dashcam.DashcamStatus.ONLINE);
                    d.setRegisteredAt(java.time.LocalDateTime.now());
                    return d;
                });

        dashcam.setImei(imei);
        dashcam.setManufacturerId(manufacturerId.toString());
        dashcam.setModel(terminalModel.toString());
        dashcam.setStatus(Dashcam.DashcamStatus.ONLINE);
        dashcam.setLastHeartbeat(java.time.LocalDateTime.now());
        dashcam.setAuthCode(authCode);
        dashcamRepository.save(dashcam);

        return encoder.buildRegistrationResponse(simPhone, seq, authCode, 0);
    }

    private byte[] handleAuthentication(byte[] frame, String simPhone, int seq, byte[] body) {
        String authCode = new String(body, StandardCharsets.UTF_8).trim();
        log.info("[JT808] Authentication: sim={}, authCode={}", simPhone, authCode);

        dashcamRepository.findBySimPhone(simPhone).ifPresent(d -> {
            d.setStatus(Dashcam.DashcamStatus.ONLINE);
            d.setLastHeartbeat(java.time.LocalDateTime.now());
            dashcamRepository.save(d);
        });

        return encoder.buildGeneralResponse(simPhone, seq, 0x0102, 0);
    }

    private byte[] handleHeartbeat(String simPhone, int seq) {
        log.debug("[JT808] Heartbeat from sim={}", simPhone);
        dashcamRepository.updateHeartbeat(simPhone);
        return encoder.buildGeneralResponse(simPhone, seq, 0x0002, 0);
    }

    private byte[] handleLogout(String simPhone, int seq, String clientId) {
        log.info("[JT808] Logout from sim={}", simPhone);
        dashcamRepository.markOffline(simPhone);
        return encoder.buildGeneralResponse(simPhone, seq, 0x0003, 0);
    }

    private byte[] handleLocationReport(String simPhone, int seq, byte[] body) {
        if (body.length < 28) {
            log.warn("[JT808] Location body too short: {} bytes", body.length);
            return encoder.buildGeneralResponse(simPhone, seq, 0x0200, 1);
        }

        int alarmFlag = ((body[0] & 0xFF) << 24) | ((body[1] & 0xFF) << 16) |
                        ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
        int statusFlag = ((body[4] & 0xFF) << 24) | ((body[5] & 0xFF) << 16) |
                         ((body[6] & 0xFF) << 8) | (body[7] & 0xFF);

        int latRaw = ((body[8] & 0xFF) << 24) | ((body[9] & 0xFF) << 16) |
                     ((body[10] & 0xFF) << 8) | (body[11] & 0xFF);
        int lngRaw = ((body[12] & 0xFF) << 24) | ((body[13] & 0xFF) << 16) |
                     ((body[14] & 0xFF) << 8) | (body[15] & 0xFF);

        double latitude = latRaw / 1_000_000.0;
        double longitude = lngRaw / 1_000_000.0;

        int altitude = ((body[16] & 0xFF) << 8) | (body[17] & 0xFF);
        int speed = ((body[18] & 0xFF) << 8) | (body[19] & 0xFF);
        int direction = ((body[20] & 0xFF) << 8) | (body[21] & 0xFF);

        int year = (body[22] & 0xFF);
        int month = (body[23] & 0xFF);
        int day = (body[24] & 0xFF);
        int hour = (body[25] & 0xFF);
        int minute = (body[26] & 0xFF);
        int second = (body[27] & 0xFF);

        boolean cameraMalfunction = (alarmFlag & 0x800) != 0;

        log.debug("[JT808] Location: sim={}, lat={}, lng={}, speed={}km/h, dir={}°, time=20{}-{}-{} {}:{}:{}{}",
                simPhone, latitude, longitude, speed, direction,
                String.format("%02d", year), String.format("%02d", month), String.format("%02d", day),
                String.format("%02d", hour), String.format("%02d", minute), String.format("%02d", second),
                cameraMalfunction ? " [CAMERA_ALARM]" : "");

        return encoder.buildGeneralResponse(simPhone, seq, 0x0200, 0);
    }

    private byte[] handleMultimediaEvent(String simPhone, int seq, byte[] body) {
        if (body.length < 14) return encoder.buildGeneralResponse(simPhone, seq, 0x0800, 1);

        int multimediaId = ((body[0] & 0xFF) << 24) | ((body[1] & 0xFF) << 16) |
                           ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
        int mediaType = body[4] & 0xFF;
        int channelId = body[5] & 0xFF;
        int eventCode = body[6] & 0xFF;

        log.info("[JT808] Multimedia event: sim={}, mmId={}, type={}, ch={}, event={}",
                simPhone, multimediaId, mediaType, channelId, eventCode);

        return encoder.buildGeneralResponse(simPhone, seq, 0x0800, 0);
    }

    private byte[] handleMultimediaData(String simPhone, int seq, byte[] body, OutputStream out) {
        if (body.length < 16) return null;

        int multimediaId = ((body[0] & 0xFF) << 24) | ((body[1] & 0xFF) << 16) |
                           ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
        int mediaType = body[4] & 0xFF;
        int channelId = body[5] & 0xFF;
        int packetFormat = body[6] & 0xFF;
        int pkgTotal = ((body[7] & 0xFF) << 8) | (body[8] & 0xFF);
        int pkgIndex = ((body[9] & 0xFF) << 8) | (body[10] & 0xFF);

        log.debug("[JT808] Multimedia data: sim={}, mmId={}, type={}, ch={}, pkg={}/{}, bodyLen={}",
                simPhone, multimediaId, mediaType, channelId, pkgIndex, pkgTotal, body.length - 16);

        if (packetFormat == 0 || pkgIndex == pkgTotal) {
            log.info("[JT808] Multimedia upload complete: mmId={}", multimediaId);
            return encoder.buildMultimediaUploadResponse(simPhone, multimediaId, 0);
        }
        return null;
    }

    private byte[] handleStoredMultimediaList(String simPhone, int seq, byte[] body) {
        log.info("[JT808] Stored multimedia list from sim={}, bodyLen={}", simPhone, body.length);
        return encoder.buildGeneralResponse(simPhone, seq, 0x0802, 0);
    }

    private byte[] handleCameraCommandResult(String simPhone, int seq, byte[] body) {
        if (body.length < 3) return encoder.buildGeneralResponse(simPhone, seq, 0x0805, 1);

        int responseSeq = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        int result = body[2] & 0xFF;
        long multimediaId = 0;
        if (body.length >= 7) {
            multimediaId = ((long)(body[3] & 0xFF) << 24) | ((long)(body[4] & 0xFF) << 16) |
                           ((long)(body[5] & 0xFF) << 8) | (body[6] & 0xFF);
        }

        log.info("[JT808] Camera command result: sim={}, result={}, mmId={}", simPhone, result, multimediaId);
        return encoder.buildGeneralResponse(simPhone, seq, 0x0805, 0);
    }

    private byte[] handleAvAttributesResponse(String simPhone, int seq, byte[] body) {
        log.info("[JT808] A/V attributes response from sim={}, bodyLen={}", simPhone, body.length);

        if (body.length >= 3) {
            int channelCount = body[2] & 0xFF;
            log.info("[JT808] T98 reports {} A/V channels", channelCount);

            java.util.List<Integer> channels = new java.util.ArrayList<>();
            int offset = 3;
            for (int i = 0; i < channelCount && offset + 4 <= body.length; i++) {
                int logicalChannel = body[offset + 1] & 0xFF;
                channels.add(logicalChannel);
                offset += 4;
            }

            dashcamRepository.findBySimPhone(simPhone).ifPresent(d -> {
                StringBuilder chJson = new StringBuilder("[");
                for (int i = 0; i < channels.size(); i++) {
                    if (i > 0) chJson.append(",");
                    chJson.append(channels.get(i));
                }
                chJson.append("]");
                dashcamRepository.updateCapabilities(d.getId(), chJson.toString(), "[]", "[98,99]");
            });
        }

        return encoder.buildGeneralResponse(simPhone, seq, 0x1003, 0);
    }

    private byte[] handleTerminalGeneralResponse(String simPhone, int seq, byte[] body) {
        if (body.length >= 5) {
            int respSeq = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
            int respMsgId = ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
            int result = body[4] & 0xFF;
            log.info("[JT808] Terminal general response: sim={}, respSeq={}, respMsgId=0x{}, result={}",
                    simPhone, respSeq, String.format("%04X", respMsgId), result);
        }
        return null;
    }

    private byte[] handleStreamStatus(String simPhone, int seq, byte[] body) {
        if (body.length >= 3) {
            int channelId = body[0] & 0xFF;
            int status = body[1] & 0xFF;
            log.info("[JT808] Stream status: sim={}, ch={}, status={}", simPhone, channelId, status);

            if (status == 1) {
                log.warn("[JT808] Stream failed for sim={}, ch={}", simPhone, channelId);
            }
        }
        return encoder.buildGeneralResponse(simPhone, seq, 0x9105, 0);
    }
}
