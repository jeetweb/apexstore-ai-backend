package com.ecom.service;

import com.ecom.dto.CheckoutRequest;
import com.ecom.exception.InsufficientStockException;
import com.ecom.exception.PaymentFailedException;
import com.ecom.exception.ResourceNotFoundException;
import com.ecom.model.*;
import com.ecom.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    public OrderService(OrderRepository orderRepository,
                        CartService cartService,
                        InventoryService inventoryService,
                        PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order placeOrder(User user, CheckoutRequest request) {
        Cart cart = cartService.getOrCreateCart(user);

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Your cart is empty. Please add items before checking out.");
        }

        // 1. Verify Inventory Availability for all items before any changes
        for (CartItem item : cart.getItems()) {
            if (!inventoryService.isAvailable(item.getProduct().getId(), item.getQuantity())) {
                throw new InsufficientStockException("Item '" + item.getProduct().getName()
                        + "' has insufficient inventory for the requested quantity (" + item.getQuantity() + ").");
            }
        }

        // 2. Build Address
        Address address = new Address(
                request.getFullName(),
                request.getStreetAddress(),
                request.getCity(),
                request.getState(),
                request.getPostalCode(),
                request.getCountry(),
                request.getPhone()
        );

        // 3. Create Order
        String orderNumber = "ORD-" + System.currentTimeMillis() % 1000000 + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Order order = new Order(orderNumber, user, address);

        BigDecimal subtotal = cart.getSubtotal();
        BigDecimal shippingFee = cartService.calculateShippingFee(subtotal, request.getShippingMethod());
        BigDecimal tax = cartService.calculateTax(subtotal);
        BigDecimal total = cartService.calculateTotal(subtotal, shippingFee, tax);

        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setTax(tax);
        order.setTotalAmount(total);

        // 4. Attach Items to Order
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem(
                    order,
                    cartItem.getProduct(),
                    cartItem.getQuantity(),
                    cartItem.getUnitPrice()
            );
            order.addItem(orderItem);
        }

        // 5. Decrement inventory atomically
        for (CartItem item : cart.getItems()) {
            inventoryService.decrementStock(item.getProduct().getId(), item.getQuantity());
        }

        // 6. Save order initial state
        Order savedOrder = orderRepository.save(order);

        // 7. Process Digital Payment
        Payment payment = paymentService.processDigitalPayment(savedOrder, request);
        savedOrder.setPayment(payment);

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            savedOrder.setStatus(OrderStatus.PAID);
            savedOrder.setTrackingNumber("TRK-EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            // Clear cart upon successful order
            cartService.clearCart(user);
        } else {
            savedOrder.setStatus(OrderStatus.PENDING_PAYMENT);
            throw new PaymentFailedException("Payment authorization failed: " + payment.getFailureReason());
        }

        return orderRepository.save(savedOrder);
    }

    public List<Order> getOrdersForUser(User user) {
        return orderRepository.findByUserOrderByOrderDateDesc(user);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with number: " + orderNumber));
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = getOrderById(orderId);
        order.setStatus(status);
        return orderRepository.save(order);
    }
}
