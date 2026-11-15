package com.ecom.service;

import com.ecom.exception.InsufficientStockException;
import com.ecom.exception.ResourceNotFoundException;
import com.ecom.model.Product;
import com.ecom.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public boolean isAvailable(Long productId, int requestedQuantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return product.isActive() && product.getStockQuantity() >= requestedQuantity;
    }

    public int getAvailableStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return product.getStockQuantity();
    }

    @Transactional
    public void decrementStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (!product.isActive()) {
            throw new InsufficientStockException("Product '" + product.getName() + "' is no longer available.");
        }

        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Available: "
                    + product.getStockQuantity() + ", requested: " + quantity);
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
    }

    @Transactional
    public void incrementStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);
    }

    @Transactional
    public Product updateStock(Long productId, int newStockQuantity, Integer lowStockThreshold) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        product.setStockQuantity(newStockQuantity);
        if (lowStockThreshold != null) {
            product.setLowStockThreshold(lowStockThreshold);
        }
        return productRepository.save(product);
    }

    public List<Product> getLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    public InventoryStats getInventoryStats() {
        long totalProducts = productRepository.count();
        long lowStockCount = productRepository.countLowStockProducts();
        long outOfStockCount = productRepository.countOutOfStockProducts();
        return new InventoryStats(totalProducts, lowStockCount, outOfStockCount);
    }

    public record InventoryStats(long totalProducts, long lowStockCount, long outOfStockCount) {}
}
