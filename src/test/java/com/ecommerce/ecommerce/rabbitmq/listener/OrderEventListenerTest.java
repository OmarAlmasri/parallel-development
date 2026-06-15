package com.ecommerce.ecommerce.rabbitmq.listener;

import com.ecommerce.ecommerce.cart.event.OrderPlacedEvent;
import com.ecommerce.ecommerce.report.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private EmailService emailService;

    @Test
    void onOrderPlaced_sendsConfirmationEmail() {
        OrderEventListener listener = new OrderEventListener(emailService);
        OrderPlacedEvent event = orderPlacedEvent();

        listener.onOrderPlaced(event);

        verify(emailService).sendOrderConfirmation("shopper1@example.com", 10L, "25.00");
    }

    @Test
    void onOrderPlaced_doesNotPropagateEmailFailures() {
        OrderEventListener listener = new OrderEventListener(emailService);
        OrderPlacedEvent event = orderPlacedEvent();
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService)
                .sendOrderConfirmation("shopper1@example.com", 10L, "25.00");

        assertDoesNotThrow(() -> listener.onOrderPlaced(event));
    }

    private OrderPlacedEvent orderPlacedEvent() {
        return new OrderPlacedEvent(
                10L,
                2L,
                "shopper1@example.com",
                new BigDecimal("25.00"),
                LocalDateTime.now()
        );
    }
}
