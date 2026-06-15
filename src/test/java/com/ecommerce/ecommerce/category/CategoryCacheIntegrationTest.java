package com.ecommerce.ecommerce.category;

import com.ecommerce.ecommerce.category.dto.CategoryRequestDTO;
import com.ecommerce.ecommerce.category.dto.CategoryResponseDTO;
import com.ecommerce.ecommerce.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class CategoryCacheIntegrationTest extends IntegrationTestSupport {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void setUpCategoryCacheTest() {
        clearCache("categories");
        clearCache("productLists");
    }

    @Test
    void getAllCategories_shouldPopulateCategoriesCache() {
        Category category = new Category();
        category.setName("Cached Category");
        categoryRepository.save(category);

        Cache categoriesCache = cacheManager.getCache("categories");
        assertNotNull(categoriesCache);
        assertNull(categoriesCache.get("all"));

        List<CategoryResponseDTO> categories = categoryService.getAllCategories();

        assertEquals(1, categories.size());
        assertEquals("Cached Category", categories.get(0).getName());
        assertNotNull(categoriesCache.get("all"));
    }

    @Test
    void createCategory_shouldEvictCategoriesAndProductListCaches() {
        categoryService.getAllCategories();
        Cache categoriesCache = cacheManager.getCache("categories");
        Cache productListsCache = cacheManager.getCache("productLists");
        assertNotNull(categoriesCache);
        assertNotNull(productListsCache);

        productListsCache.put("all", List.of());
        assertNotNull(categoriesCache.get("all"));
        assertNotNull(productListsCache.get("all"));

        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName("New Cached Category");
        categoryService.createCategory(request);

        assertNull(categoriesCache.get("all"));
        assertNull(productListsCache.get("all"));
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
