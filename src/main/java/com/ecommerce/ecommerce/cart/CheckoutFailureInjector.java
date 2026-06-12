package com.ecommerce.ecommerce.cart;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CheckoutFailureInjector {

    private final boolean failAfterInventoryUpdate;

    public CheckoutFailureInjector(
            @Value("${app.checkout.fail-after-inventory-update:false}") boolean failAfterInventoryUpdate) {
        this.failAfterInventoryUpdate = failAfterInventoryUpdate;
    }

    public void afterInventoryUpdate() {
        if (failAfterInventoryUpdate) {
            throw new RuntimeException("Injected checkout failure after inventory update");
        }
    }
}
