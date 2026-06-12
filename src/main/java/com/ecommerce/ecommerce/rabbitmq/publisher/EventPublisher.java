package com.ecommerce.ecommerce.rabbitmq.publisher;

import com.ecommerce.ecommerce.rabbitmq.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final RabbitTemplate rabbitTemplate;
    private final boolean eventsEnabled;

    public EventPublisher(RabbitTemplate rabbitTemplate,
                          @Value("${app.events.enabled:true}") boolean eventsEnabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.eventsEnabled = eventsEnabled;
    }

    public void publish(String routingKey, Object event) {
        if (!eventsEnabled) {
            log.debug("Event publishing disabled. Skipping routing key: {}", routingKey);
            return;
        }

        log.info("Publishing event with routing key: {}", routingKey);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
    }
}
