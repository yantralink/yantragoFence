package com.yantrago.gateway.queue;

import com.yantrago.shared.queue.QueueNames;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue configuration — declares all exchanges, queues, and bindings for the gateway.
 *
 * This is a supplementary config that ensures all topology is declared even if
 * RabbitMqConfig is not loaded (e.g. in test contexts). In production, RabbitMqConfig
 * handles the full topology.
 *
 * Per AGENTS.md rule 17: use QueueNames from shared module, no ad-hoc names.
 */
@Configuration
public class QueueConfig {

    @Bean
    public DirectExchange commandExchange() {
        return ExchangeBuilder.directExchange(QueueNames.COMMAND_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange commandResultExchange() {
        return ExchangeBuilder.directExchange(QueueNames.COMMAND_RESULT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange telemetryExchange() {
        return ExchangeBuilder.directExchange(QueueNames.TELEMETRY_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deviceEventExchange() {
        return ExchangeBuilder.directExchange(QueueNames.DEVICE_EVENT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange locationExchange() {
        return ExchangeBuilder.directExchange(QueueNames.LOCATION_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue commandQueue() {
        return QueueBuilder.durable(QueueNames.COMMAND_QUEUE).build();
    }

    @Bean
    public Binding commandBinding(DirectExchange commandExchange, Queue commandQueue) {
        return BindingBuilder.bind(commandQueue)
                .to(commandExchange)
                .with(QueueNames.COMMAND_ROUTING_KEY);
    }
}
