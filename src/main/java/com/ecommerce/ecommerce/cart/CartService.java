package com.ecommerce.ecommerce.cart;

import com.ecommerce.ecommerce.cart.dto.*;
import com.ecommerce.ecommerce.inventory.InventoryLockService;
import com.ecommerce.ecommerce.product.Product;
import com.ecommerce.ecommerce.product.ProductRepository;
import com.ecommerce.ecommerce.users.User;
import com.ecommerce.ecommerce.users.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CheckoutTransactionService checkoutTransactionService;
    private final InventoryLockService inventoryLockService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository,
                       CheckoutTransactionService checkoutTransactionService,
                       InventoryLockService inventoryLockService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.checkoutTransactionService = checkoutTransactionService;
        this.inventoryLockService = inventoryLockService;
    }

    // Get or create cart for user
    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    public CartResponseDTO getCart(String email) {
        User user = findUserOrThrow(email);
        Cart cart = getOrCreateCart(user);
        return toDTO(cart);
    }

    @Transactional
    public CartResponseDTO addToCart(String email, AddToCartRequestDTO dto) {
        User user = findUserOrThrow(email);
        Cart cart = getOrCreateCart(user);
        Product product = findProductOrThrow(dto.getProductId());

        // Stock validation
        if (dto.getQuantity() > product.getStock()) {
            throw new RuntimeException(
                "Insufficient stock for product: " + product.getName() +
                ". Available: " + product.getStock()
            );
        }

        // If item already exists, update quantity
        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .ifPresentOrElse(existingItem -> {
                    int newQty = existingItem.getQuantity() + dto.getQuantity();
                    if (newQty > product.getStock()) {
                        throw new RuntimeException(
                            "Insufficient stock for product: " + product.getName() +
                            ". Available: " + product.getStock()
                        );
                    }
                    existingItem.setQuantity(newQty);
                    cartItemRepository.save(existingItem);
                }, () -> {
                    CartItem item = new CartItem();
                    item.setCart(cart);
                    item.setProduct(product);
                    item.setQuantity(dto.getQuantity());
                    cart.getItems().add(item);
                    cartItemRepository.save(item);
                });

        return toDTO(cartRepository.findById(cart.getId()).orElseThrow());
    }

    @Transactional
    public CartResponseDTO updateCartItem(String email, Long productId, UpdateCartItemRequestDTO dto) {
        User user = findUserOrThrow(email);
        Cart cart = getOrCreateCart(user);
        Product product = findProductOrThrow(productId);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new RuntimeException("Item not found in cart"));

        if (dto.getQuantity() > product.getStock()) {
            throw new RuntimeException(
                "Insufficient stock for product: " + product.getName() +
                ". Available: " + product.getStock()
            );
        }

        item.setQuantity(dto.getQuantity());
        cartItemRepository.save(item);
        return toDTO(cartRepository.findById(cart.getId()).orElseThrow());
    }

    @Transactional
    public CartResponseDTO removeCartItem(String email, Long productId) {
        User user = findUserOrThrow(email);
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new RuntimeException("Item not found in cart"));

        cartItemRepository.delete(item);
        cart.getItems().remove(item);
        return toDTO(cartRepository.findById(cart.getId()).orElseThrow());
    }

    @Transactional
    public CartResponseDTO clearCart(String email) {
        User user = findUserOrThrow(email);
        Cart cart = getOrCreateCart(user);
        cart.getItems().clear();
        cartRepository.save(cart);
        return toDTO(cart);
    }

    public CheckoutResponseDTO checkout(String email) {
        List<Long> productIds = cartItemRepository.findDistinctProductIdsByCartUserEmail(email);
        return inventoryLockService.executeWithProductLocks(
                productIds,
                () -> checkoutTransactionService.checkout(email)
        );
    }

    // ---- Helpers ----

    private User findUserOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
    }

    private CartResponseDTO toDTO(Cart cart) {
        List<CartItemResponseDTO> itemDTOs = cart.getItems().stream()
                .map(item -> new CartItemResponseDTO(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getProduct().getPrice(),
                        item.getProduct().getPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity()))
                ))
                .collect(Collectors.toList());

        BigDecimal grandTotal = itemDTOs.stream()
                .map(CartItemResponseDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponseDTO(cart.getId(), itemDTOs, grandTotal);
    }
}
