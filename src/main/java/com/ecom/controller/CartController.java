package com.ecom.controller;

import com.ecom.exception.InsufficientStockException;
import com.ecom.model.Cart;
import com.ecom.model.User;
import com.ecom.service.CartService;
import com.ecom.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final UserService userService;

    public CartController(CartService cartService, UserService userService) {
        this.cartService = cartService;
        this.userService = userService;
    }

    @GetMapping
    public String viewCart(Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        Cart cart = cartService.getOrCreateCart(user);

        BigDecimal subtotal = cart.getSubtotal();
        BigDecimal shipping = cartService.calculateShippingFee(subtotal, "STANDARD");
        BigDecimal tax = cartService.calculateTax(subtotal);
        BigDecimal total = cartService.calculateTotal(subtotal, shipping, tax);

        // Progress towards free shipping ($50)
        BigDecimal freeShippingThreshold = CartService.FREE_SHIPPING_THRESHOLD;
        BigDecimal freeShippingRemaining = freeShippingThreshold.subtract(subtotal).max(BigDecimal.ZERO);
        double freeShippingPercent = Math.min(100.0, (subtotal.doubleValue() / freeShippingThreshold.doubleValue()) * 100.0);

        model.addAttribute("cart", cart);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", shipping);
        model.addAttribute("tax", tax);
        model.addAttribute("total", total);
        model.addAttribute("freeShippingThreshold", freeShippingThreshold);
        model.addAttribute("freeShippingRemaining", freeShippingRemaining);
        model.addAttribute("freeShippingPercent", freeShippingPercent);

        return "cart/cart";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam("productId") Long productId,
                            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
                            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                            RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        try {
            cartService.addItemToCart(user, productId, quantity);
            redirectAttributes.addFlashAttribute("successMessage", "Item successfully added to your cart!");
        } catch (InsufficientStockException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/cart";
    }

    @PostMapping("/update")
    public String updateQuantity(@RequestParam("productId") Long productId,
                                 @RequestParam("quantity") int quantity,
                                 RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        try {
            cartService.updateItemQuantity(user, productId, quantity);
            redirectAttributes.addFlashAttribute("successMessage", "Cart updated.");
        } catch (InsufficientStockException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/remove/{productId}")
    public String removeItem(@PathVariable("productId") Long productId,
                             RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        cartService.removeItemFromCart(user, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Item removed from cart.");
        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clearCart(RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        cartService.clearCart(user);
        redirectAttributes.addFlashAttribute("successMessage", "Cart cleared.");
        return "redirect:/cart";
    }
}
