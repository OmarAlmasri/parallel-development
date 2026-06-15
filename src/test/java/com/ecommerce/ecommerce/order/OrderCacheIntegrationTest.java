package com.ecommerce.ecommerce.order;

import com.ecommerce.ecommerce.cart.Cart;
import com.ecommerce.ecommerce.cart.CartItem;
import com.ecommerce.ecommerce.cart.CartItemRepository;
import com.ecommerce.ecommerce.cart.CartRepository;
import com.ecommerce.ecommerce.cart.CartService;
import com.ecommerce.ecommerce.category.Category;
import com.ecommerce.ecommerce.category.CategoryRepository;
import com.ecommerce.ecommerce.order.dto.OrderResponseDTO;
import com.ecommerce.ecommerce.product.Product;
import com.ecommerce.ecommerce.product.ProductRepository;
import com.ecommerce.ecommerce.support.IntegrationTestSupport;
import com.ecommerce.ecommerce.users.User;
import com.ecommerce.ecommerce.users.UserRepository;
import com.ecommerce.ecommerce.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class OrderCacheIntegrationTest extends IntegrationTestSupport {

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void setUpOrderCacheTest() {
        clearCache("userOrders");
        clearCache("orderDetails");
    }

    @Test
    void getMyOrders_shouldPopulateUserOrdersCache() {
        TestData testData = createOrderData("cached-orders@example.com");
        Cache userOrdersCache = cacheManager.getCache("userOrders");
        assertNotNull(userOrdersCache);
        assertNull(userOrdersCache.get(testData.userEmail()));

        List<OrderResponseDTO> orders = orderService.getMyOrders(testData.userEmail());

        assertEquals(1, orders.size());
        assertNotNull(userOrdersCache.get(testData.userEmail()));
    }

    @Test
    void getOrderById_shouldPopulateOrderDetailsCacheByUserAndOrder() {
        TestData testData = createOrderData("cached-order-detail@example.com");
        Cache orderDetailsCache = cacheManager.getCache("orderDetails");
        String cacheKey = testData.userEmail() + ":" + testData.orderId();
        assertNotNull(orderDetailsCache);
        assertNull(orderDetailsCache.get(cacheKey));

        OrderResponseDTO order = orderService.getOrderById(testData.orderId(), testData.userEmail());

        assertEquals(testData.orderId(), order.getId());
        assertNotNull(orderDetailsCache.get(cacheKey));
    }

    @Test
    void checkout_shouldEvictUserOrdersCacheAfterCommit() {
        CheckoutData checkoutData = createCheckoutData();
        Cache userOrdersCache = cacheManager.getCache("userOrders");
        assertNotNull(userOrdersCache);

        orderService.getMyOrders(checkoutData.userEmail());
        assertNotNull(userOrdersCache.get(checkoutData.userEmail()));

        cartService.checkout(checkoutData.userEmail());

        assertNull(userOrdersCache.get(checkoutData.userEmail()));
    }

    private TestData createOrderData(String email) {
        Category category = createCategory("Order Cache");
        Product product = createProduct(category, "Cached Order Product", new BigDecimal("50.00"), 10);
        User user = createUser(email, new BigDecimal("1000.00"));

        Order order = new Order();
        order.setUser(user);
        order.setTotalPrice(new BigDecimal("50.00"));
        order.setStatus(OrderStatus.SUCCESS);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPriceAtPurchase(product.getPrice());
        order.getItems().add(item);

        order = orderRepository.save(order);
        return new TestData(user.getEmail(), order.getId());
    }

    private CheckoutData createCheckoutData() {
        Category category = createCategory("Checkout Order Cache");
        Product product = createProduct(category, "Checkout Cached Product", new BigDecimal("100.00"), 5);
        User user = createUser("checkout-order-cache@example.com", new BigDecimal("1000.00"));

        Cart cart = new Cart();
        cart.setUser(user);
        cart = cartRepository.save(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(2);
        cartItemRepository.save(item);

        return new CheckoutData(user.getEmail());
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private Product createProduct(Category category, String name, BigDecimal price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Cache test product");
        product.setPrice(price);
        product.setStock(stock);
        product.setCategory(category);
        return productRepository.save(product);
    }

    private User createUser(String email, BigDecimal balance) {
        User user = new User();
        user.setName("Cache User");
        user.setEmail(email);
        user.setPassword("password123");
        user.setRole(UserRole.USER);
        user.setBalance(balance);
        return userRepository.save(user);
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private record TestData(String userEmail, Long orderId) {
    }

    private record CheckoutData(String userEmail) {
    }
}
