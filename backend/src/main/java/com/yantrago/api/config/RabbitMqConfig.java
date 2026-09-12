package com.yantrago.api.config;

import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration — declares all exchanges, queues, and bindings
 * using QueueNames constants from the shared module.
 *
 * Per AGENTS.md rule 17: shared module defines RabbitMQ contracts.
 * Both backend and device-gateway must reference these constants.
 *
 * Architecture (AGENTS.md rule 4): device communication is asynchronous
 * via RabbitMQ between backend and gateway.
 */
@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    // ===== Exchanges (all direct exchanges) =====

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
    public DirectExchange alertEventExchange() {
        return ExchangeBuilder.directExchange(QueueNames.ALERT_EVENT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange locationExchange() {
        return ExchangeBuilder.directExchange(QueueNames.LOCATION_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange notificationExchange() {
        return ExchangeBuilder.directExchange(QueueNames.NOTIFICATION_EXCHANGE).durable(true).build();
    }

    // ===== Queues =====

    @Bean
    public Queue commandQueue() {
        return QueueBuilder.durable(QueueNames.COMMAND_QUEUE).build();
    }

    @Bean
    public Queue commandResultQueue() {
        return QueueBuilder.durable(QueueNames.COMMAND_RESULT_QUEUE).build();
    }

    @Bean
    public Queue telemetryQueue() {
        return QueueBuilder.durable(QueueNames.TELEMETRY_QUEUE).build();
    }

    @Bean
    public Queue deviceEventQueue() {
        return QueueBuilder.durable(QueueNames.DEVICE_EVENT_QUEUE).build();
    }

    @Bean
    public Queue alertEventQueue() {
        return QueueBuilder.durable(QueueNames.ALERT_EVENT_QUEUE).build();
    }

    @Bean
    public Queue locationQueue() {
        return QueueBuilder.durable(QueueNames.LOCATION_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QueueNames.NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(QueueNames.NOTIFICATION_DLQ).build();
    }

    // ===== Bindings =====

    @Bean
    public Binding commandBinding() {
        return BindingBuilder.bind(commandQueue())
                .to(commandExchange())
                .with(QueueNames.COMMAND_ROUTING_KEY);
    }

    @Bean
    public Binding commandResultBinding() {
        return BindingBuilder.bind(commandResultQueue())
                .to(commandResultExchange())
                .with(QueueNames.COMMAND_RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding telemetryBinding() {
        return BindingBuilder.bind(telemetryQueue())
                .to(telemetryExchange())
                .with(QueueNames.TELEMETRY_ROUTING_KEY);
    }

    @Bean
    public Binding deviceEventBinding() {
        return BindingBuilder.bind(deviceEventQueue())
                .to(deviceEventExchange())
                .with(QueueNames.DEVICE_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding alertEventBinding() {
        return BindingBuilder.bind(alertEventQueue())
                .to(alertEventExchange())
                .with(QueueNames.ALERT_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding locationBinding() {
        return BindingBuilder.bind(locationQueue())
                .to(locationExchange())
                .with(QueueNames.LOCATION_ROUTING_KEY);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(notificationExchange())
                .with(QueueNames.NOTIFICATION_ROUTING_KEY);
    }

    // ===== RabbitAdmin (needed by NotificationHealthIndicators) =====

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    // ===== Message converter (JSON) =====

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonJsonMessageConverter());
        // Phase 1 fix: enable publisher confirms and mandatory routing
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData instanceof OutboxCorrelationData outboxData) {
                if (ack) {
                    outboxData.markPublished();
                } else {
                    outboxData.markNacked(cause);
                }
            }
        });
        template.setReturnsCallback(returned -> {
            log.error("RabbitMQ message returned (unroutable): replyCode={} replyText={} exchange={} routingKey={}",
                    returned.getReplyCode(), returned.getReplyText(),
                    returned.getExchange(), returned.getRoutingKey());
            if (returned.getMessage() != null) {
                Object correlation = returned.getMessage().getMessageProperties().getCorrelationId();
                if (correlation != null) {
                    log.error("Unroutable message correlation: {}", correlation);
                }
            }
        });
        return template;
    }
}
