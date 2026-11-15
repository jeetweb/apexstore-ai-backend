package com.ecom.controller;

import com.ecom.model.Product;
import com.ecom.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    private final ProductService productService;

    public HomeController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String index(@RequestParam(name = "category", required = false) String category,
                        @RequestParam(name = "query", required = false) String query,
                        Model model) {
        List<Product> products = productService.searchProducts(category, query);
        model.addAttribute("products", products);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", query);
        return "index";
    }
}
