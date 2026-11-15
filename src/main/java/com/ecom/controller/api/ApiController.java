package com.ecom.controller.api;

import com.ecom.dto.AddToCartRequest;
import com.ecom.dto.CheckoutRequest;
import com.ecom.dto.InventoryUpdateDto;
import com.ecom.dto.ProductDto;
import com.ecom.model.Cart;
import com.ecom.model.Order;
import com.ecom.model.Product;
import com.ecom.model.User;
import com.ecom.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "E-Commerce REST API", description = "Endpoints for Catalog, Cart, Inventory, and Digital Checkout")
public class ApiController {

    private final ProductService productService;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final UserService userService;

    public ApiController(ProductService productService,
                         CartService cartService,
                         InventoryService inventoryService,
                         OrderService orderService,
                         UserService userService) {
        this.productService = productService;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.userService = userService;
    }

    // --- Product Endpoints ---

    @GetMapping("/products")
    @Operation(summary = "Get active products with optional category and search filters")
    public List<Product> getProducts(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String query) {
        return productService.searchProducts(category, query);
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get product details by ID")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    // --- Cart Endpoints ---

    @GetMapping("/cart")
    @Operation(summary = "Get the current authenticated user's shopping cart")
    public ResponseEntity<?> getCart() {
        return userService.getCurrentUser()
                .map(user -> ResponseEntity.ok(cartService.getOrCreateCart(user)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/cart/items")
    @Operation(summary = "Add an item to the shopping cart")
    public ResponseEntity<?> addToCart(@Valid @RequestBody AddToCartRequest req) {
        return userService.getCurrentUser()
                .map(user -> {
                    Cart cart = cartService.addItemToCart(user, req.getProductId(), req.getQuantity());
                    return ResponseEntity.ok(cart);
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @DeleteMapping("/cart/items/{productId}")
    @Operation(summary = "Remove an item from the shopping cart")
    public ResponseEntity<?> removeFromCart(@PathVariable Long productId) {
        return userService.getCurrentUser()
                .map(user -> {
                    Cart cart = cartService.removeItemFromCart(user, productId);
                    return ResponseEntity.ok(cart);
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // --- Inventory Endpoints ---

    @GetMapping("/inventory/stats")
    @Operation(summary = "Get inventory health statistics")
    public InventoryService.InventoryStats getInventoryStats() {
        return inventoryService.getInventoryStats();
    }

    @GetMapping("/inventory/low-stock")
    @Operation(summary = "Get all products at or below low stock threshold")
    public List<Product> getLowStockProducts() {
        return inventoryService.getLowStockProducts();
    }

    @PutMapping("/inventory/{productId}")
    @Operation(summary = "Update product stock quantity and threshold")
    public ResponseEntity<?> updateInventory(@PathVariable Long productId,
                                             @Valid @RequestBody InventoryUpdateDto dto) {
        dto.setProductId(productId);
        Product updated = inventoryService.updateStock(productId, dto.getStockQuantity(), dto.getLowStockThreshold());
        return ResponseEntity.ok(updated);
    }

    // --- Checkout & Payment Endpoint ---

    @PostMapping("/checkout")
    @Operation(summary = "Execute atomic checkout with digital payment processing and inventory decrement")
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest request) {
        return userService.getCurrentUser()
                .map(user -> {
                    try {
                        Order order = orderService.placeOrder(user, request);
                        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                                "status", "SUCCESS",
                                "orderNumber", order.getOrderNumber(),
                                "transactionId", order.getPayment().getTransactionId(),
                                "amount", order.getTotalAmount(),
                                "paymentMethod", order.getPayment().getPaymentMethod(),
                                "paymentStatus", order.getPayment().getPaymentStatus(),
                                "trackingNumber", order.getTrackingNumber(),
                                "orderStatus", order.getStatus()
                        ));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
