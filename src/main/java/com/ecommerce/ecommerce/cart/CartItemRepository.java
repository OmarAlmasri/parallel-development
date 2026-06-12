package com.ecommerce.ecommerce.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Query("select distinct item.product.id from CartItem item where item.cart.user.email = :email")
    List<Long> findDistinctProductIdsByCartUserEmail(@Param("email") String email);
}
