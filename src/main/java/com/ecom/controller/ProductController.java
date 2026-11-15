package com.ecom.controller;

import com.ecom.model.Product;
import com.ecom.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String listProducts(@RequestParam(name = "category", required = false) String category,
                               @RequestParam(name = "query", required = false) String query,
                               Model model) {
        List<Product> products = productService.searchProducts(category, query);
        model.addAttribute("products", products);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchQuery", query);
        return "products/list";
    }

    @GetMapping("/{slug}")
    public String productDetails(@PathVariable("slug") String slug, Model model) {
        Product product = productService.getProductBySlug(slug);
        model.addAttribute("product", product);
        return "products/detail";
    }
}
