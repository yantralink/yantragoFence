package com.yantrago.shared.queue;

/**
 * RabbitMQ exchange, queue, and routing-key name constants shared by
 * the backend API and the device gateway. Both services must reference
 * these constants so that producer/consumer bindings always agree.
 */
public final class QueueNames {

    private QueueNames() {
        throw new UnsupportedOperationException("Constants only");
    }

    // ----- Exchanges -----
    public static final String COMMAND_EXCHANGE = "yantrago.command";
    public static final String COMMAND_RESULT_EXCHANGE = "yantrago.command.result";
    public static final String TELEMETRY_EXCHANGE = "yantrago.telemetry";
    public static final String DEVICE_EVENT_EXCHANGE = "yantrago.device.event";
    public static final String ALERT_EVENT_EXCHANGE = "yantrago.alert.event";
    public static final String LOCATION_EXCHANGE = "yantrago.location";

    // ----- Queues -----
    public static final String COMMAND_QUEUE = "yantrago.command.queue";
    public static final String COMMAND_RESULT_QUEUE = "yantrago.command.result.queue";
    public static final String TELEMETRY_QUEUE = "yantrago.telemetry.queue";
    public static final String DEVICE_EVENT_QUEUE = "yantrago.device.event.queue";
    public static final String ALERT_EVENT_QUEUE = "yantrago.alert.event.queue";
    public static final String LOCATION_QUEUE = "yantrago.location.queue";

    // ----- Routing keys -----
    public static final String COMMAND_ROUTING_KEY = "command";
    public static final String COMMAND_RESULT_ROUTING_KEY = "command.result";
    public static final String TELEMETRY_ROUTING_KEY = "telemetry";
    public static final String DEVICE_EVENT_ROUTING_KEY = "device.event";
    public static final String ALERT_EVENT_ROUTING_KEY = "alert.event";
    public static final String LOCATION_ROUTING_KEY = "location";
}
