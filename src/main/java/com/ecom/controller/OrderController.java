package com.ecom.controller;

import com.ecom.model.Order;
import com.ecom.model.User;
import com.ecom.service.OrderService;
import com.ecom.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @GetMapping
    public String orderHistory(Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        List<Order> orders = orderService.getOrdersForUser(user);
        model.addAttribute("orders", orders);
        return "orders/history";
    }

    @GetMapping("/{orderNumber}")
    public String orderDetails(@PathVariable("orderNumber") String orderNumber, Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        Order order = orderService.getOrderByNumber(orderNumber);

        if (!order.getUser().getId().equals(user.getId()) && !user.getRole().name().equals("ROLE_ADMIN")) {
            return "redirect:/orders";
        }

        model.addAttribute("order", order);
        return "orders/detail";
    }
}
