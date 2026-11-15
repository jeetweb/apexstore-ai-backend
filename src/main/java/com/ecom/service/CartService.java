package com.ecom.service;

import com.ecom.exception.InsufficientStockException;
import com.ecom.exception.ResourceNotFoundException;
import com.ecom.model.Cart;
import com.ecom.model.CartItem;
import com.ecom.model.Product;
import com.ecom.model.User;
import com.ecom.repository.CartItemRepository;
import com.ecom.repository.CartRepository;
import com.ecom.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");
    public static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("5.99");
    public static final BigDecimal EXPRESS_SHIPPING_FEE = new BigDecimal("19.99");
    public static final BigDecimal TAX_RATE = new BigDecimal("0.08"); // 8%

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository, InventoryService inventoryService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = new Cart(user);
                    return cartRepository.save(newCart);
                });
    }

    @Transactional
    public Cart addItemToCart(User user, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (!product.isActive()) {
            throw new InsufficientStockException("Product is currently unavailable");
        }

        Cart cart = getOrCreateCart(user);

        // Check how much is already in the cart
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        int existingQty = existingItem.map(CartItem::getQuantity).orElse(0);
        int totalRequested = existingQty + quantity;

        if (product.getStockQuantity() < totalRequested) {
            throw new InsufficientStockException("Cannot add " + quantity + " more. Only "
                    + product.getStockQuantity() + " in stock (you already have " + existingQty + " in your cart).");
        }

        cart.addItem(product, quantity);
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart updateItemQuantity(User user, Long productId, int quantity) {
        Cart cart = getOrCreateCart(user);

        if (quantity <= 0) {
            cart.removeItem(productId);
            return cartRepository.save(cart);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException("Cannot update quantity to " + quantity + ". Only "
                    + product.getStockQuantity() + " available in stock.");
        }

        cart.updateItemQuantity(productId, quantity);
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart removeItemFromCart(User user, Long productId) {
        Cart cart = getOrCreateCart(user);
        cart.removeItem(productId);
        return cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(User user) {
        Cart cart = getOrCreateCart(user);
        cart.clear();
        cartRepository.save(cart);
    }

    public BigDecimal calculateShippingFee(BigDecimal subtotal, String shippingMethod) {
        if ("EXPRESS".equalsIgnoreCase(shippingMethod)) {
            return EXPRESS_SHIPPING_FEE;
        }
        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        return STANDARD_SHIPPING_FEE;
    }

    public BigDecimal calculateTax(BigDecimal subtotal) {
        return subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotal(BigDecimal subtotal, BigDecimal shipping, BigDecimal tax) {
        return subtotal.add(shipping).add(tax).setScale(2, RoundingMode.HALF_UP);
    }
}
