package com.ecommerce.ecommerce.cart;

import com.ecommerce.ecommerce.cart.dto.CheckoutResponseDTO;
import com.ecommerce.ecommerce.cart.event.OrderPlacedEvent;
import com.ecommerce.ecommerce.exception.InventoryConflictException;
import com.ecommerce.ecommerce.order.Order;
import com.ecommerce.ecommerce.order.OrderItem;
import com.ecommerce.ecommerce.order.OrderRepository;
import com.ecommerce.ecommerce.order.OrderStatus;
import com.ecommerce.ecommerce.product.Product;
import com.ecommerce.ecommerce.product.ProductRepository;
import com.ecommerce.ecommerce.rabbitmq.config.RabbitMQConfig;
import com.ecommerce.ecommerce.rabbitmq.publisher.EventPublisher;
import com.ecommerce.ecommerce.transaction.Transaction;
import com.ecommerce.ecommerce.transaction.TransactionRepository;
import com.ecommerce.ecommerce.transaction.TransactionType;
import com.ecommerce.ecommerce.users.User;
import com.ecommerce.ecommerce.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CheckoutTransactionService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutTransactionService.class);

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisher eventPublisher;
    private final CacheManager cacheManager;
    private final CheckoutFailureInjector checkoutFailureInjector;

    public CheckoutTransactionService(CartRepository cartRepository,
                                      ProductRepository productRepository,
                                      UserRepository userRepository,
                                      OrderRepository orderRepository,
                                      TransactionRepository transactionRepository,
                                      EventPublisher eventPublisher,
                                      CacheManager cacheManager,
                                      CheckoutFailureInjector checkoutFailureInjector) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
        this.checkoutFailureInjector = checkoutFailureInjector;
    }

    @Transactional
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            noRetryFor = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2.0)
    )
    public CheckoutResponseDTO checkout(String email) {
        User user = findUserOrThrow(email);
        Cart cart = getOrCreateCart(user);

        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        List<String> skippedItems = new ArrayList<>();
        List<OrderItem> validOrderItems = new ArrayList<>();
        Set<Long> purchasedProductIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            if (product.getStock() < cartItem.getQuantity()) {
                skippedItems.add(product.getName() +
                        " (requested: " + cartItem.getQuantity() +
                        ", available: " + product.getStock() + ")");
                continue;
            }

            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtPurchase(product.getPrice());
            validOrderItems.add(orderItem);
        }

        if (validOrderItems.isEmpty()) {
            throw new RuntimeException("No items could be fulfilled. All items are out of stock.");
        }

        if (user.getBalance().compareTo(total) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        user.setBalance(user.getBalance().subtract(total));
        userRepository.save(user);

        for (OrderItem orderItem : validOrderItems) {
            Product product = orderItem.getProduct();
            product.setStock(product.getStock() - orderItem.getQuantity());
            productRepository.save(product);
            purchasedProductIds.add(product.getId());
        }

        productRepository.flush();
        userRepository.flush();
        checkoutFailureInjector.afterInventoryUpdate();

        Order order = new Order();
        order.setUser(user);
        order.setTotalPrice(total);
        order.setStatus(OrderStatus.SUCCESS);
        order = orderRepository.save(order);

        for (OrderItem orderItem : validOrderItems) {
            orderItem.setOrder(order);
        }
        order.setItems(validOrderItems);
        orderRepository.save(order);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(total);
        transaction.setType(TransactionType.PURCHASE);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        cart.getItems().clear();
        cartRepository.save(cart);
        orderRepository.flush();
        transactionRepository.flush();
        cartRepository.flush();

        String message = skippedItems.isEmpty()
                ? "Checkout successful"
                : "Checkout partially successful. Some items were skipped.";

        publishOrderPlacedAfterCommit(
                new OrderPlacedEvent(
                    order.getId(),
                    user.getId(),
                    user.getEmail(),
                    total,
                    LocalDateTime.now()
                )
        );

        evictProductCachesAfterCommit(purchasedProductIds);

        return new CheckoutResponseDTO(order.getId(), total, skippedItems, message);
    }

    @Recover
    public CheckoutResponseDTO recover(OptimisticLockingFailureException ex, String email) {
        throw new InventoryConflictException("Checkout conflicted with another purchase. Please try again.");
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    private User findUserOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    private void publishOrderPlacedAfterCommit(OrderPlacedEvent event) {
        Runnable publish = () -> {
            try {
                eventPublisher.publish(RabbitMQConfig.ORDER_PLACED_KEY, event);
            } catch (RuntimeException ex) {
                log.error("Failed to publish order placed event after checkout commit. orderId={}", event.getOrderId(), ex);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private void evictProductCachesAfterCommit(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }

        Runnable eviction = () -> {
            Cache productsCache = cacheManager.getCache("products");
            if (productsCache != null) {
                productIds.forEach(productsCache::evict);
            }

            Cache productListsCache = cacheManager.getCache("productLists");
            if (productListsCache != null) {
                productListsCache.clear();
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }
}
