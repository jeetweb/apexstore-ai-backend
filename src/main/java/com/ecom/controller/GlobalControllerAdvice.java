package com.ecom.controller;

import com.ecom.model.Cart;
import com.ecom.model.Category;
import com.ecom.model.User;
import com.ecom.service.CartService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final UserService userService;
    private final CartService cartService;
    private final ProductService productService;

    public GlobalControllerAdvice(UserService userService, CartService cartService, ProductService productService) {
        this.userService = userService;
        this.cartService = cartService;
        this.productService = productService;
    }

    @ModelAttribute("currentUser")
    public User populateCurrentUser() {
        return userService.getCurrentUser().orElse(null);
    }

    @ModelAttribute("cartItemCount")
    public int populateCartItemCount() {
        Optional<User> user = userService.getCurrentUser();
        if (user.isPresent()) {
            Cart cart = cartService.getOrCreateCart(user.get());
            return cart.getTotalQuantity();
        }
        return 0;
    }

    @ModelAttribute("navCategories")
    public List<Category> populateCategories() {
        return productService.getAllCategories();
    }
}
