package com.ecommerce.ecommerce.product;


import com.ecommerce.ecommerce.category.Category;
import com.ecommerce.ecommerce.category.CategoryRepository;
import com.ecommerce.ecommerce.category.dto.CategoryResponseDTO;
import com.ecommerce.ecommerce.product.dto.ProductRequestDTO;
import com.ecommerce.ecommerce.product.dto.ProductResponseDTO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Cacheable(cacheNames = "productLists", key = "#categoryId == null ? 'all' : 'category:' + #categoryId")
    public List<ProductResponseDTO> getAllProducts(Long categoryId) {
        List<Product> products = (categoryId != null)
                ? productRepository.findByCategoryId(categoryId)
                : productRepository.findAll();
        return products.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "products", key = "#id")
    public ProductResponseDTO getProductById(Long id) {
        return toDTO(findByIdOrThrow(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "productLists", allEntries = true)
    public ProductResponseDTO createProduct(ProductRequestDTO dto) {
        Category category = findCategoryOrThrow(dto.getCategoryId());
        Product product = new Product();
        mapDtoToProduct(dto, product, category);
        return toDTO(productRepository.save(product));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "products", key = "#id"),
            @CacheEvict(cacheNames = "productLists", allEntries = true)
    })
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO dto) {
        Product product = findByIdOrThrow(id);
        Category category = findCategoryOrThrow(dto.getCategoryId());
        mapDtoToProduct(dto, product, category);
        return toDTO(productRepository.save(product));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "products", key = "#id"),
            @CacheEvict(cacheNames = "productLists", allEntries = true)
    })
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    // ---- Helpers ----

    private void mapDtoToProduct(ProductRequestDTO dto, Product product, Category category) {
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCategory(category);
    }

    private Product findByIdOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + categoryId));
    }

    public ProductResponseDTO toDTO(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                new CategoryResponseDTO(
                        product.getCategory().getId(),
                        product.getCategory().getName()
                ),
                product.getCreatedAt()
        );
    }
}
