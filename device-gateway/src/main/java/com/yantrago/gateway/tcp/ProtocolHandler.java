package com.yantrago.gateway.tcp;

/**
 * Abstraction for GPS device protocol handlers.
 * New manufacturers can be added by implementing this interface and
 * registering as a Spring {@code @Component}.
 */
public interface ProtocolHandler {

    /**
     * Returns the protocol name (e.g. "CONCOX_V5").
     */
    String getProtocolName();

    /**
     * Checks whether this handler can process a packet based on its first bytes.
     *
     * @param firstBytes the initial bytes of the received data
     * @return true if this handler recognizes the protocol
     */
    boolean canHandle(byte[] firstBytes);

    /**
     * Handles a single packet and returns the response bytes (or null for no response).
     *
     * @param packet   raw packet bytes
     * @param clientId client identifier for connection tracking
     * @return response bytes, or null
     */
    byte[] handlePacket(byte[] packet, String clientId);

    /**
     * Returns the IMEI associated with a client connection (after login).
     *
     * @param clientId client identifier
     * @return IMEI string, or null if not yet logged in
     */
    String getImeiForClient(String clientId);

    /**
     * Removes the client entry from the handler's internal tracking map.
     * Called when a client disconnects to prevent memory leaks.
     *
     * @param clientId client identifier
     */
    default void removeClient(String clientId) {}
}
