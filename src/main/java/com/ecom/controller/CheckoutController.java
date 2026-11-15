package com.ecom.controller;

import com.ecom.dto.CheckoutRequest;
import com.ecom.exception.InsufficientStockException;
import com.ecom.exception.PaymentFailedException;
import com.ecom.model.Cart;
import com.ecom.model.Order;
import com.ecom.model.PaymentMethod;
import com.ecom.model.User;
import com.ecom.service.CartService;
import com.ecom.service.OrderService;
import com.ecom.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final UserService userService;

    public CheckoutController(CartService cartService, OrderService orderService, UserService userService) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.userService = userService;
    }

    @GetMapping
    public String checkoutPage(Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        Cart cart = cartService.getOrCreateCart(user);

        if (cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your cart is empty. Please add items before checking out.");
            return "redirect:/cart";
        }

        if (!model.containsAttribute("checkoutRequest")) {
            CheckoutRequest request = new CheckoutRequest();
            request.setFullName(user.getFullName());
            request.setPhone(user.getPhone());
            if (user.getDefaultAddress() != null) {
                request.setStreetAddress(user.getDefaultAddress().getStreetAddress());
                request.setCity(user.getDefaultAddress().getCity());
                request.setState(user.getDefaultAddress().getState());
                request.setPostalCode(user.getDefaultAddress().getPostalCode());
                request.setCountry(user.getDefaultAddress().getCountry());
            }
            request.setPaymentMethod(PaymentMethod.CREDIT_CARD);
            request.setShippingMethod("STANDARD");
            model.addAttribute("checkoutRequest", request);
        }

        prepareOrderSummary(model, cart, "STANDARD");
        return "checkout/checkout";
    }

    @PostMapping("/process")
    public String processCheckout(@Valid @ModelAttribute("checkoutRequest") CheckoutRequest request,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        Cart cart = cartService.getOrCreateCart(user);

        if (cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your cart is empty.");
            return "redirect:/cart";
        }

        if (bindingResult.hasErrors()) {
            prepareOrderSummary(model, cart, request.getShippingMethod());
            return "checkout/checkout";
        }

        try {
            Order order = orderService.placeOrder(user, request);
            redirectAttributes.addFlashAttribute("successMessage", "Payment confirmed! Your order has been placed.");
            return "redirect:/checkout/success/" + order.getOrderNumber();
        } catch (InsufficientStockException | PaymentFailedException ex) {
            prepareOrderSummary(model, cart, request.getShippingMethod());
            model.addAttribute("errorMessage", ex.getMessage());
            return "checkout/checkout";
        } catch (Exception ex) {
            prepareOrderSummary(model, cart, request.getShippingMethod());
            model.addAttribute("errorMessage", "An error occurred while processing checkout: " + ex.getMessage());
            return "checkout/checkout";
        }
    }

    @GetMapping("/success/{orderNumber}")
    public String checkoutSuccess(@PathVariable("orderNumber") String orderNumber, Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        Order order = orderService.getOrderByNumber(orderNumber);

        // Security check: ensure order belongs to current user (unless admin)
        if (!order.getUser().getId().equals(user.getId()) && !user.getRole().name().equals("ROLE_ADMIN")) {
            return "redirect:/";
        }

        model.addAttribute("order", order);
        return "checkout/success";
    }

    private void prepareOrderSummary(Model model, Cart cart, String shippingMethod) {
        BigDecimal subtotal = cart.getSubtotal();
        BigDecimal shipping = cartService.calculateShippingFee(subtotal, shippingMethod);
        BigDecimal tax = cartService.calculateTax(subtotal);
        BigDecimal total = cartService.calculateTotal(subtotal, shipping, tax);

        model.addAttribute("cart", cart);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", shipping);
        model.addAttribute("tax", tax);
        model.addAttribute("total", total);
        model.addAttribute("paymentMethods", PaymentMethod.values());
    }
}
