package com.yantrago.gateway.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Routes incoming TCP packets to the appropriate protocol handler.
 * Spring injects all {@link ProtocolHandler} implementations automatically.
 */
@Component
public class ProtocolRouter {

    private static final Logger log = LoggerFactory.getLogger(ProtocolRouter.class);

    private final List<ProtocolHandler> handlers;

    public ProtocolRouter(List<ProtocolHandler> handlers) {
        this.handlers = handlers;
        log.info("ProtocolRouter initialized with {} handler(s): {}",
                handlers.size(),
                handlers.stream().map(ProtocolHandler::getProtocolName).toList());
    }

    /**
     * Finds the protocol handler that can process the given data.
     *
     * @param firstBytes initial bytes of the received data
     * @return matching handler, or null if no handler matches
     */
    public ProtocolHandler findHandler(byte[] firstBytes) {
        for (ProtocolHandler handler : handlers) {
            if (handler.canHandle(firstBytes)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Convenience method: finds a handler and delegates packet handling.
     *
     * @param packet   raw packet bytes
     * @param clientId client identifier
     * @return response bytes, or null if no handler matches
     */
    public byte[] handlePacket(byte[] packet, String clientId) {
        ProtocolHandler handler = findHandler(packet);
        if (handler == null) {
            log.warn("No protocol handler found for packet from client {}", clientId);
            return null;
        }
        return handler.handlePacket(packet, clientId);
    }
}
