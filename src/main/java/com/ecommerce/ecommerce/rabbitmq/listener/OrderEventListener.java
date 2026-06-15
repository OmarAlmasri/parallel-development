package com.ecommerce.ecommerce.rabbitmq.listener;

import com.ecommerce.ecommerce.cart.event.OrderPlacedEvent;
import com.ecommerce.ecommerce.rabbitmq.config.RabbitMQConfig;
import com.ecommerce.ecommerce.report.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final EmailService emailNotificationService;

    public OrderEventListener(EmailService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_PLACED_QUEUE)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Order placed event received for orderId: {}", event.getOrderId());

        try {
            emailNotificationService.sendOrderConfirmation(
                event.getUserEmail(),
                event.getOrderId(),
                event.getTotalPrice().toString()
            );
        } catch (RuntimeException ex) {
            log.error(
                "Order confirmation email failed. orderId={} userEmail={}",
                event.getOrderId(),
                event.getUserEmail(),
                ex
            );
        }
    }
}
