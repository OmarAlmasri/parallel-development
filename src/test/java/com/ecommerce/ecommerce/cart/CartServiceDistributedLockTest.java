package com.ecommerce.ecommerce.cart;

import com.ecommerce.ecommerce.cart.dto.CheckoutResponseDTO;
import com.ecommerce.ecommerce.inventory.InventoryLockService;
import com.ecommerce.ecommerce.product.ProductRepository;
import com.ecommerce.ecommerce.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceDistributedLockTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CheckoutTransactionService checkoutTransactionService;
    @Mock private InventoryLockService inventoryLockService;

    @Test
    void checkout_shouldAcquireInventoryLocks_beforeCallingTransactionalCheckout() {
        CartService cartService = new CartService(
                cartRepository,
                cartItemRepository,
                productRepository,
                userRepository,
                checkoutTransactionService,
                inventoryLockService
        );
        String email = "shopper@example.com";
        List<Long> productIds = List.of(3L, 1L);
        CheckoutResponseDTO response = new CheckoutResponseDTO(7L, BigDecimal.TEN, List.of(), "Checkout successful");

        when(cartItemRepository.findDistinctProductIdsByCartUserEmail(email)).thenReturn(productIds);
        when(checkoutTransactionService.checkout(email)).thenReturn(response);
        when(inventoryLockService.executeWithProductLocks(eq(productIds), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<CheckoutResponseDTO> action = invocation.getArgument(1);
            verify(checkoutTransactionService, never()).checkout(email);
            return action.get();
        });

        CheckoutResponseDTO result = cartService.checkout(email);

        assertSame(response, result);

        InOrder inOrder = inOrder(cartItemRepository, inventoryLockService, checkoutTransactionService);
        inOrder.verify(cartItemRepository).findDistinctProductIdsByCartUserEmail(email);
        inOrder.verify(inventoryLockService).executeWithProductLocks(eq(productIds), any());
        inOrder.verify(checkoutTransactionService).checkout(email);
    }
}
