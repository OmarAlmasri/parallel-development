package com.ecommerce.ecommerce.cart;

import com.ecommerce.ecommerce.category.Category;
import com.ecommerce.ecommerce.category.CategoryRepository;
import com.ecommerce.ecommerce.order.OrderItemRepository;
import com.ecommerce.ecommerce.order.OrderRepository;
import com.ecommerce.ecommerce.product.Product;
import com.ecommerce.ecommerce.product.ProductRepository;
import com.ecommerce.ecommerce.support.IntegrationTestSupport;
import com.ecommerce.ecommerce.transaction.TransactionRepository;
import com.ecommerce.ecommerce.users.User;
import com.ecommerce.ecommerce.users.UserRepository;
import com.ecommerce.ecommerce.users.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "app.checkout.fail-after-inventory-update=true")
@ActiveProfiles("test")
class CheckoutTransactionRollbackIntegrationTest extends IntegrationTestSupport {

    @Autowired private CartService cartService;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void checkout_shouldRollbackPaymentAndInventory_whenFailureHappensBeforeOrderCreation() {
        TestData testData = createCheckoutData();

        assertThrows(RuntimeException.class, () -> cartService.checkout(testData.userEmail()));

        Product product = productRepository.findById(testData.productId()).orElseThrow();
        User user = userRepository.findByEmail(testData.userEmail()).orElseThrow();

        assertEquals(5, product.getStock());
        assertEquals(0, new BigDecimal("1000.00").compareTo(user.getBalance()));
        assertEquals(0, orderRepository.findAll().size());
        assertEquals(0, orderItemRepository.findAll().size());
        assertEquals(0, transactionRepository.findAll().size());
        assertEquals(1, cartItemRepository.findAll().size());
    }

    private TestData createCheckoutData() {
        Category category = new Category();
        category.setName("ACID Rollback");
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Rollback Product");
        product.setDescription("Used for ACID rollback tests");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setCategory(category);
        product = productRepository.save(product);

        User user = new User();
        user.setName("Rollback User");
        user.setEmail("acid-rollback@example.com");
        user.setPassword("password123");
        user.setRole(UserRole.USER);
        user.setBalance(new BigDecimal("1000.00"));
        user = userRepository.save(user);

        Cart cart = new Cart();
        cart.setUser(user);
        cart = cartRepository.save(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(2);
        cartItemRepository.save(item);

        return new TestData(user.getEmail(), product.getId());
    }

    private record TestData(String userEmail, Long productId) {
    }
}
