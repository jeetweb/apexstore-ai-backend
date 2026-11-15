package com.ecom.service;

import com.ecom.dto.CheckoutRequest;
import com.ecom.exception.InsufficientStockException;
import com.ecom.exception.PaymentFailedException;
import com.ecom.model.*;
import com.ecom.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class EcomFlowIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        Category cat = categoryRepository.findBySlug("electronics")
                .orElseGet(() -> categoryRepository.save(new Category("Electronics", "electronics", "Tech", "fa-laptop")));

        testUser = userRepository.findByUsername("testcustomer").orElseGet(() -> {
            User u = new User("testcustomer", "test@test.com", passwordEncoder.encode("pass123"), "Test User", "1234567890", Role.ROLE_CUSTOMER);
            return userRepository.save(u);
        });

        testProduct1 = productRepository.save(new Product(
                "Test Laptop", "test-laptop-" + System.currentTimeMillis(), "Description",
                new BigDecimal("100.00"), cat, "https://example.com/laptop.jpg", "TEST-SKU-1", 10
        ));

        testProduct2 = productRepository.save(new Product(
                "Limited Mouse", "limited-mouse-" + System.currentTimeMillis(), "Description",
                new BigDecimal("50.00"), cat, "https://example.com/mouse.jpg", "TEST-SKU-2", 2
        ));
    }

    @Test
    @DisplayName("1. User registration and authentication")
    void testUserRegistration() {
        String uniqueUsername = "alice_" + System.currentTimeMillis();
        com.ecom.dto.RegisterRequest req = new com.ecom.dto.RegisterRequest();
        req.setUsername(uniqueUsername);
        req.setEmail(uniqueUsername + "@example.com");
        req.setPassword("secret123");
        req.setFullName("Alice Wonderland");
        req.setPhone("5551234567");

        User registered = userService.registerUser(req);
        assertThat(registered.getId()).isNotNull();
        assertThat(registered.getRole()).isEqualTo(Role.ROLE_CUSTOMER);
        assertThat(passwordEncoder.matches("secret123", registered.getPassword())).isTrue();
    }

    @Test
    @DisplayName("2. Add to cart, update quantity, and subtotal calculation")
    void testCartOperations() {
        // Add product 1 (qty 2)
        Cart cart = cartService.addItemToCart(testUser, testProduct1.getId(), 2);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotalQuantity()).isEqualTo(2);
        assertThat(cart.getSubtotal()).isEqualByComparingTo(new BigDecimal("200.00"));

        // Free shipping for >= 50
        BigDecimal shipping = cartService.calculateShippingFee(cart.getSubtotal(), "STANDARD");
        assertThat(shipping).isEqualByComparingTo(BigDecimal.ZERO);

        // Update quantity
        cart = cartService.updateItemQuantity(testUser, testProduct1.getId(), 5);
        assertThat(cart.getTotalQuantity()).isEqualTo(5);
        assertThat(cart.getSubtotal()).isEqualByComparingTo(new BigDecimal("500.00"));

        // Remove item
        cart = cartService.removeItemFromCart(testUser, testProduct1.getId());
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("3. Inventory stock guard prevents adding more than available stock")
    void testInventoryExceededInCart() {
        // Product 2 only has 2 in stock
        assertThrows(InsufficientStockException.class, () -> {
            cartService.addItemToCart(testUser, testProduct2.getId(), 5);
        });
    }

    @Test
    @DisplayName("4. Digital payment processing: Credit Card Success")
    void testCreditCardPaymentSuccess() {
        Order mockOrder = new Order("ORD-TEST-1", testUser, null);
        mockOrder.setTotalAmount(new BigDecimal("150.00"));
        mockOrder = orderRepository.save(mockOrder);

        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        req.setCardNumber("4242 4242 4242 4242");
        req.setCardExpiry("12/28");
        req.setCardCvv("123");

        Payment payment = paymentService.processDigitalPayment(mockOrder, req);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getTransactionId()).startsWith("TXN-CREDIT_CARD-");
        assertThat(payment.getPaymentDetails()).contains("Visa ending in 4242");
    }

    @Test
    @DisplayName("5. Digital payment processing: Card Declined simulation (ending with 0000)")
    void testCreditCardPaymentDeclined() {
        Order mockOrder = new Order("ORD-TEST-FAIL", testUser, null);
        mockOrder.setTotalAmount(new BigDecimal("150.00"));
        Order savedOrder = orderRepository.save(mockOrder);

        CheckoutRequest req = new CheckoutRequest();
        req.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        req.setCardNumber("4111 1111 1111 0000"); // ends in 0000
        req.setCardExpiry("12/28");
        req.setCardCvv("123");

        assertThrows(PaymentFailedException.class, () -> {
            paymentService.processDigitalPayment(savedOrder, req);
        });
    }

    @Test
    @DisplayName("6. Digital payment processing: UPI and Wallet")
    void testUpiAndWalletPayments() {
        Order mockOrder = new Order("ORD-TEST-UPI", testUser, null);
        mockOrder.setTotalAmount(new BigDecimal("75.00"));
        mockOrder = orderRepository.save(mockOrder);

        // UPI Success
        CheckoutRequest upiReq = new CheckoutRequest();
        upiReq.setPaymentMethod(PaymentMethod.UPI);
        upiReq.setUpiId("customer@okaxis");
        Payment upiPayment = paymentService.processDigitalPayment(mockOrder, upiReq);
        assertThat(upiPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(upiPayment.getPaymentDetails()).contains("UPI ID: customer@okaxis");

        // Digital Wallet Success
        Order walletOrder = new Order("ORD-TEST-WALLET", testUser, null);
        walletOrder.setTotalAmount(new BigDecimal("99.00"));
        walletOrder = orderRepository.save(walletOrder);

        CheckoutRequest walletReq = new CheckoutRequest();
        walletReq.setPaymentMethod(PaymentMethod.DIGITAL_WALLET);
        walletReq.setWalletProvider("Apple Pay");
        Payment walletPayment = paymentService.processDigitalPayment(walletOrder, walletReq);
        assertThat(walletPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(walletPayment.getPaymentDetails()).contains("Apple Pay");
    }

    @Test
    @DisplayName("7. Complete End-to-End Checkout Flow with Inventory Decrement & Cart Clear")
    void testCompleteCheckoutFlow() {
        int initialStock = inventoryService.getAvailableStock(testProduct1.getId());
        assertThat(initialStock).isEqualTo(10);

        // Add 3 to cart
        cartService.addItemToCart(testUser, testProduct1.getId(), 3);

        // Prepare checkout request
        CheckoutRequest req = new CheckoutRequest();
        req.setFullName("Test User");
        req.setStreetAddress("123 Tech Lane");
        req.setCity("Metropolis");
        req.setState("NY");
        req.setPostalCode("10001");
        req.setCountry("USA");
        req.setPhone("5559876543");
        req.setShippingMethod("STANDARD");
        req.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        req.setCardNumber("4532 1111 2222 3333");
        req.setCardExpiry("10/29");
        req.setCardCvv("456");

        // Place Order
        Order placedOrder = orderService.placeOrder(testUser, req);

        // Verify Order Details
        assertThat(placedOrder).isNotNull();
        assertThat(placedOrder.getOrderNumber()).startsWith("ORD-");
        assertThat(placedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(placedOrder.getTrackingNumber()).isNotNull();
        assertThat(placedOrder.getPayment()).isNotNull();
        assertThat(placedOrder.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Verify Inventory was decremented atomically: 10 - 3 = 7
        int remainingStock = inventoryService.getAvailableStock(testProduct1.getId());
        assertThat(remainingStock).isEqualTo(7);

        // Verify user cart was cleared
        Cart userCart = cartService.getOrCreateCart(testUser);
        assertThat(userCart.getItems()).isEmpty();
    }
}
