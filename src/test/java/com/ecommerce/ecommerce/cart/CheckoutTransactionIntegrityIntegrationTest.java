package com.ecommerce.ecommerce.cart;

import com.ecommerce.ecommerce.cart.dto.CheckoutResponseDTO;
import com.ecommerce.ecommerce.category.Category;
import com.ecommerce.ecommerce.category.CategoryRepository;
import com.ecommerce.ecommerce.order.OrderItemRepository;
import com.ecommerce.ecommerce.order.OrderRepository;
import com.ecommerce.ecommerce.product.Product;
import com.ecommerce.ecommerce.product.ProductRepository;
import com.ecommerce.ecommerce.support.IntegrationTestSupport;
import com.ecommerce.ecommerce.transaction.Transaction;
import com.ecommerce.ecommerce.transaction.TransactionRepository;
import com.ecommerce.ecommerce.transaction.TransactionType;
import com.ecommerce.ecommerce.users.User;
import com.ecommerce.ecommerce.users.UserRepository;
import com.ecommerce.ecommerce.users.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutTransactionIntegrityIntegrationTest extends IntegrationTestSupport {

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
    void checkout_shouldCommitPaymentInventoryOrderTransactionAndCartTogether() {
        TestData testData = createCheckoutData();

        CheckoutResponseDTO response = cartService.checkout(testData.userEmail());

        Product product = productRepository.findById(testData.productId()).orElseThrow();
        User user = userRepository.findByEmail(testData.userEmail()).orElseThrow();
        Transaction transaction = transactionRepository.findAll().get(0);

        assertNotNull(response.getOrderId());
        assertEquals(0, new BigDecimal("200.00").compareTo(response.getTotalCharged()));
        assertEquals(3, product.getStock());
        assertEquals(0, new BigDecimal("800.00").compareTo(user.getBalance()));
        assertEquals(1, orderRepository.findAll().size());
        assertEquals(1, orderItemRepository.findAll().size());
        assertEquals(1, transactionRepository.findAll().size());
        assertEquals(TransactionType.PURCHASE, transaction.getType());
        assertEquals(0, new BigDecimal("200.00").compareTo(transaction.getAmount()));
        assertEquals(0, cartItemRepository.findAll().size());
    }

    private TestData createCheckoutData() {
        Category category = new Category();
        category.setName("ACID");
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Transactional Product");
        product.setDescription("Used for ACID success tests");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setCategory(category);
        product = productRepository.save(product);

        User user = new User();
        user.setName("ACID User");
        user.setEmail("acid-success@example.com");
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
