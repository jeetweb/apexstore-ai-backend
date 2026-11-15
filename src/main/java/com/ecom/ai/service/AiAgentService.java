package com.ecom.ai.service;

import com.ecom.ai.dto.AiChatResponse;
import com.ecom.ai.dto.SemanticSearchResult;
import com.ecom.exception.InsufficientStockException;
import com.ecom.model.Order;
import com.ecom.model.Product;
import com.ecom.model.User;
import com.ecom.repository.ProductRepository;
import com.ecom.service.CartService;
import com.ecom.service.InventoryService;
import com.ecom.service.OrderService;
import com.ecom.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Agentic Assistant Service.
 * Implements deterministic Tool/Function Calling over OCC/eCommerce APIs:
 * - check_inventory (Live SKU & stock status)
 * - track_order (OMS order & tracking verification)
 * - search_catalog (Vector semantic search)
 * - add_to_cart (Stateful cart manipulation)
 */
@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final CartService cartService;
    private final UserService userService;
    private final VectorSearchService vectorSearchService;

    private static final Pattern ORDER_NUM_PATTERN = Pattern.compile("(ORD-[0-9A-Z\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKU_PATTERN = Pattern.compile("([A-Z]{3,4}-[A-Z0-9]{3,4}-[0-9]{2,4})", Pattern.CASE_INSENSITIVE);

    public AiAgentService(ProductRepository productRepository,
                          InventoryService inventoryService,
                          OrderService orderService,
                          CartService cartService,
                          UserService userService,
                          VectorSearchService vectorSearchService) {
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.cartService = cartService;
        this.userService = userService;
        this.vectorSearchService = vectorSearchService;
    }

    public AiChatResponse processMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new AiChatResponse(
                    "Hello! I am your ApexStore AI Shopping & Fulfillment Assistant. How can I help you today?",
                    "none",
                    null,
                    List.of("Show me laptops under $2000", "Track order ORD-...", "Check inventory for LAP-APX-16")
            );
        }

        String msg = userMessage.trim();
        String lower = msg.toLowerCase(Locale.ROOT);

        // 1. Tool Call: Track Order (OMS)
        Matcher orderMatcher = ORDER_NUM_PATTERN.matcher(msg);
        if (orderMatcher.find() || lower.contains("track") || lower.contains("status of my order")) {
            String orderNumber = orderMatcher.find(0) ? orderMatcher.group(1).toUpperCase() : extractCandidateOrderNumber(msg);
            if (orderNumber != null) {
                return executeTrackOrderTool(orderNumber);
            } else {
                return new AiChatResponse(
                        "I would be glad to check your order! Please provide your order number (e.g., ORD-123456-ABCD).",
                        "prompt_order_number",
                        null,
                        List.of("Check my recent orders", "View Order History")
                );
            }
        }

        // 2. Tool Call: Check Inventory / SKU Stock
        Matcher skuMatcher = SKU_PATTERN.matcher(msg);
        if (skuMatcher.find() || lower.contains("stock") || lower.contains("available") || lower.contains("inventory")) {
            String targetSku = skuMatcher.find(0) ? skuMatcher.group(1).toUpperCase() : null;
            return executeCheckInventoryTool(targetSku, msg);
        }

        // 3. Tool Call: Add to Cart
        if (lower.startsWith("add ") || lower.contains("add to cart") || lower.contains("buy this")) {
            return executeAddToCartTool(msg);
        }

        // 4. Tool Call: Vector Semantic Search & Discovery
        if (lower.contains("find") || lower.contains("search") || lower.contains("looking for") ||
                lower.contains("recommend") || lower.contains("under") || lower.contains("laptop") ||
                lower.contains("headphones") || lower.contains("watch") || lower.contains("keyboard") ||
                lower.contains("monitor") || lower.contains("mouse")) {
            return executeVectorSearchTool(msg);
        }

        // 5. Default General Assistance
        return new AiChatResponse(
                "I am your ApexStore AI Concierge. I can search our catalog using semantic vector search, verify live warehouse stock, add items to your cart, or track your orders.",
                "general_guidance",
                null,
                List.of(
                        "Recommend noise-cancelling headphones",
                        "Find high-performance laptops under $2000",
                        "Check stock for AUD-AURA-01"
                )
        );
    }

    // --- Deterministic Tool Handlers ---

    private AiChatResponse executeTrackOrderTool(String orderNumber) {
        log.info("Agent executing Tool: track_order [orderNumber={}]", orderNumber);
        try {
            Order order = orderService.getOrderByNumber(orderNumber);
            Map<String, Object> payload = Map.of(
                    "orderNumber", order.getOrderNumber(),
                    "status", order.getStatus().getDisplayName(),
                    "totalAmount", order.getTotalAmount(),
                    "trackingNumber", order.getTrackingNumber() != null ? order.getTrackingNumber() : "Pending Dispatch",
                    "paymentStatus", order.getPayment() != null ? order.getPayment().getPaymentStatus().name() : "N/A",
                    "itemsCount", order.getItems().size()
            );

            String reply = String.format(
                    "📦 Order **%s** is currently **%s**. Total: **$%s** (%d items). Express Tracking: `%s`.",
                    order.getOrderNumber(),
                    order.getStatus().getDisplayName(),
                    order.getTotalAmount(),
                    order.getItems().size(),
                    order.getTrackingNumber() != null ? order.getTrackingNumber() : "Preparing for carrier pickup"
            );

            return new AiChatResponse(reply, "track_order", payload, List.of("View receipt", "Browse more products"));
        } catch (Exception e) {
            return new AiChatResponse(
                    "I could not locate an order with number `" + orderNumber + "`. Please verify the code and try again.",
                    "track_order_failed",
                    Map.of("searchedNumber", orderNumber, "error", e.getMessage()),
                    List.of("View all my orders", "Contact support")
            );
        }
    }

    private AiChatResponse executeCheckInventoryTool(String targetSku, String originalQuery) {
        log.info("Agent executing Tool: check_inventory [sku={}, query={}]", targetSku, originalQuery);
        Optional<Product> productOpt = Optional.empty();

        if (targetSku != null) {
            productOpt = productRepository.findBySku(targetSku);
        }

        if (productOpt.isEmpty()) {
            // Fallback: search by product name
            List<Product> matches = productRepository.findByActiveTrue();
            for (Product p : matches) {
                if (originalQuery.toLowerCase().contains(p.getName().toLowerCase()) ||
                        originalQuery.toLowerCase().contains(p.getSlug().replace("-", " "))) {
                    productOpt = Optional.of(p);
                    break;
                }
            }
        }

        if (productOpt.isPresent()) {
            Product p = productOpt.get();
            int stock = p.getStockQuantity();
            String status = stock == 0 ? "OUT OF STOCK" : (p.isLowStock() ? "LOW STOCK" : "IN STOCK");

            Map<String, Object> payload = Map.of(
                    "productId", p.getId(),
                    "name", p.getName(),
                    "sku", p.getSku(),
                    "stockQuantity", stock,
                    "status", status,
                    "price", p.getPrice()
            );

            String reply = stock > 0
                    ? String.format("✅ **%s** (SKU: `%s`) has **%d units** available in stock at **$%s**.", p.getName(), p.getSku(), stock, p.getPrice())
                    : String.format("⚠️ **%s** (SKU: `%s`) is currently **Out of Stock**. Our team has scheduled replenishment.", p.getName(), p.getSku());

            return new AiChatResponse(reply, "check_inventory", payload, List.of(
                    "Add 1 to Cart",
                    "Find similar products",
                    "Check other items"
            ));
        }

        return new AiChatResponse(
                "Please specify the product SKU (e.g. `LAP-APX-16` or `AUD-AURA-01`) or the exact product name so I can check real-time warehouse inventory for you.",
                "check_inventory_clarification",
                null,
                List.of("Check LAP-APX-16", "Check AUD-AURA-01", "Browse catalog")
        );
    }

    private AiChatResponse executeAddToCartTool(String message) {
        log.info("Agent executing Tool: add_to_cart [query={}]", message);
        Optional<User> userOpt = userService.getCurrentUser();
        if (userOpt.isEmpty()) {
            return new AiChatResponse(
                    "🔒 Please sign in to your account first so I can link items to your shopping cart.",
                    "authentication_required",
                    null,
                    List.of("Sign In", "Sign Up")
            );
        }

        // Try extracting product ID or SKU
        Product matchedProduct = null;
        for (Product p : productRepository.findByActiveTrue()) {
            if (message.toLowerCase().contains(p.getSku().toLowerCase()) ||
                    message.toLowerCase().contains(p.getName().toLowerCase()) ||
                    message.contains("product " + p.getId())) {
                matchedProduct = p;
                break;
            }
        }

        if (matchedProduct == null) {
            return new AiChatResponse(
                    "Which product would you like me to add? You can tell me the name or SKU (e.g., 'Add Apex Pro UltraBook to cart').",
                    "add_to_cart_clarification",
                    null,
                    List.of("Add LAP-APX-16", "Add AUD-AURA-01")
            );
        }

        try {
            cartService.addItemToCart(userOpt.get(), matchedProduct.getId(), 1);
            Map<String, Object> payload = Map.of(
                    "productId", matchedProduct.getId(),
                    "name", matchedProduct.getName(),
                    "price", matchedProduct.getPrice(),
                    "sku", matchedProduct.getSku()
            );

            return new AiChatResponse(
                    String.format("🎉 Added **%s** ($%s) to your cart!", matchedProduct.getName(), matchedProduct.getPrice()),
                    "add_to_cart",
                    payload,
                    List.of("View Cart", "Proceed to Checkout", "Continue Shopping")
            );
        } catch (InsufficientStockException ex) {
            return new AiChatResponse(
                    "Could not add to cart: " + ex.getMessage(),
                    "add_to_cart_error",
                    Map.of("error", ex.getMessage()),
                    List.of("View other items")
            );
        }
    }

    private AiChatResponse executeVectorSearchTool(String query) {
        log.info("Agent executing Tool: search_catalog (Vector Search) [query={}]", query);
        List<SemanticSearchResult> results = vectorSearchService.search(query, null, 3);

        if (results.isEmpty()) {
            return new AiChatResponse(
                    "I searched our catalog embeddings but couldn't find a direct match. Try describing the features you need (e.g., 'ergonomic workspace setup' or 'wireless sound with noise cancelling').",
                    "search_catalog_empty",
                    Collections.emptyList(),
                    List.of("Show all laptops", "Show all audio", "View catalog")
            );
        }

        StringBuilder reply = new StringBuilder("Here are the best matches found via Vector Semantic Search:\n\n");
        List<Map<String, Object>> payloadList = new ArrayList<>();

        for (SemanticSearchResult res : results) {
            Product p = res.getProduct();
            int matchPercent = (int) Math.round(res.getSimilarityScore() * 100);
            reply.append(String.format("• **%s** — $%s (%d%% match, %s)\n",
                    p.getName(), p.getPrice(), matchPercent, p.isInStock() ? "In Stock" : "Out of Stock"));

            payloadList.add(Map.of(
                    "id", p.getId(),
                    "name", p.getName(),
                    "price", p.getPrice(),
                    "score", res.getSimilarityScore(),
                    "slug", p.getSlug()
            ));
        }

        return new AiChatResponse(
                reply.toString().trim(),
                "search_catalog",
                payloadList,
                List.of("View Cart", "Check stock for first match", "Filter under $500")
        );
    }

    private String extractCandidateOrderNumber(String text) {
        String[] tokens = text.split("\\s+");
        for (String t : tokens) {
            if (t.toUpperCase().startsWith("ORD-")) {
                return t.replaceAll("[^A-Za-z0-9\\-]", "").toUpperCase();
            }
        }
        return null;
    }
}
