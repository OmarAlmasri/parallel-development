package com.ecommerce.ecommerce.product;

import com.ecommerce.ecommerce.category.Category;
import com.ecommerce.ecommerce.category.CategoryRepository;
import com.ecommerce.ecommerce.product.dto.ProductRequestDTO;
import com.ecommerce.ecommerce.product.dto.ProductResponseDTO;
import com.ecommerce.ecommerce.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class ProductCacheIntegrationTest extends IntegrationTestSupport {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CacheManager cacheManager;

    private Long categoryId;

    @BeforeEach
    void setUpCacheTest() {
        clearCache("products");
        clearCache("productLists");

        Category category = new Category();
        category.setName("Cached Products");
        categoryId = categoryRepository.save(category).getId();
    }

    @Test
    void getProductById_shouldPopulateProductsCache() {
        Long productId = createProduct("Cached Laptop", "Original", new BigDecimal("900.00"), 12);
        Cache productsCache = cacheManager.getCache("products");

        assertNotNull(productsCache);
        assertNull(productsCache.get(productId));

        ProductResponseDTO product = productService.getProductById(productId);

        assertEquals("Cached Laptop", product.getName());
        assertNotNull(productsCache.get(productId));
    }

    @Test
    void updateProduct_shouldEvictProductAndProductListCaches() {
        Long productId = createProduct("Cached Phone", "Original", new BigDecimal("500.00"), 8);

        productService.getProductById(productId);
        productService.getAllProducts(null);

        Cache productsCache = cacheManager.getCache("products");
        Cache productListsCache = cacheManager.getCache("productLists");
        assertNotNull(productsCache);
        assertNotNull(productListsCache);
        assertNotNull(productsCache.get(productId));
        assertNotNull(productListsCache.get("all"));

        ProductRequestDTO update = new ProductRequestDTO();
        update.setName("Updated Phone");
        update.setDescription("Updated");
        update.setPrice(new BigDecimal("550.00"));
        update.setStock(7);
        update.setCategoryId(categoryId);

        productService.updateProduct(productId, update);

        assertNull(productsCache.get(productId));
        assertNull(productListsCache.get("all"));
    }

    private Long createProduct(String name, String description, BigDecimal price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategory(categoryRepository.findById(categoryId).orElseThrow());
        return productRepository.save(product).getId();
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
