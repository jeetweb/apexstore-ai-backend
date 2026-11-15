package com.ecom.controller;

import com.ecom.dto.InventoryUpdateDto;
import com.ecom.dto.ProductDto;
import com.ecom.model.Order;
import com.ecom.model.OrderStatus;
import com.ecom.model.Product;
import com.ecom.service.InventoryService;
import com.ecom.service.OrderService;
import com.ecom.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderService orderService;

    public AdminController(ProductService productService,
                           InventoryService inventoryService,
                           OrderService orderService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Order> allOrders = orderService.getAllOrders();
        InventoryService.InventoryStats stats = inventoryService.getInventoryStats();

        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.PENDING_PAYMENT)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("ordersCount", allOrders.size());
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("inventoryStats", stats);
        model.addAttribute("recentOrders", allOrders.stream().limit(5).toList());
        model.addAttribute("lowStockProducts", inventoryService.getLowStockProducts());

        return "admin/dashboard";
    }

    @GetMapping("/inventory")
    public String inventoryManagement(@RequestParam(value = "filter", required = false) String filter, Model model) {
        List<Product> products;
        if ("low".equalsIgnoreCase(filter)) {
            products = inventoryService.getLowStockProducts();
        } else {
            products = productService.getAllProducts();
        }

        model.addAttribute("products", products);
        model.addAttribute("filter", filter);
        model.addAttribute("stats", inventoryService.getInventoryStats());
        return "admin/inventory";
    }

    @PostMapping("/inventory/update")
    public String updateInventory(@Valid @ModelAttribute InventoryUpdateDto dto,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid stock update values");
            return "redirect:/admin/inventory";
        }

        inventoryService.updateStock(dto.getProductId(), dto.getStockQuantity(), dto.getLowStockThreshold());
        redirectAttributes.addFlashAttribute("successMessage", "Stock updated successfully!");
        return "redirect:/admin/inventory";
    }

    @PostMapping("/inventory/quick-add")
    public String quickAddStock(@RequestParam("productId") Long productId,
                                @RequestParam("amount") int amount,
                                RedirectAttributes redirectAttributes) {
        inventoryService.incrementStock(productId, amount);
        redirectAttributes.addFlashAttribute("successMessage", "Added " + amount + " units to inventory.");
        return "redirect:/admin/inventory";
    }

    @GetMapping("/products/new")
    public String newProductForm(Model model) {
        if (!model.containsAttribute("productDto")) {
            model.addAttribute("productDto", new ProductDto());
        }
        model.addAttribute("categories", productService.getAllCategories());
        return "admin/product-form";
    }

    @PostMapping("/products/new")
    public String createProduct(@Valid @ModelAttribute("productDto") ProductDto productDto,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", productService.getAllCategories());
            return "admin/product-form";
        }

        productService.createProduct(productDto);
        redirectAttributes.addFlashAttribute("successMessage", "Product created successfully!");
        return "redirect:/admin/inventory";
    }

    @GetMapping("/products/edit/{id}")
    public String editProductForm(@PathVariable("id") Long id, Model model) {
        Product product = productService.getProductById(id);
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        dto.setImageUrl(product.getImageUrl());
        dto.setSku(product.getSku());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setLowStockThreshold(product.getLowStockThreshold());
        dto.setActive(product.isActive());

        model.addAttribute("productDto", dto);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("isEdit", true);
        return "admin/product-form";
    }

    @PostMapping("/products/edit/{id}")
    public String updateProduct(@PathVariable("id") Long id,
                                @Valid @ModelAttribute("productDto") ProductDto productDto,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", productService.getAllCategories());
            model.addAttribute("isEdit", true);
            return "admin/product-form";
        }

        productService.updateProduct(id, productDto);
        redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully!");
        return "redirect:/admin/inventory";
    }

    @GetMapping("/orders")
    public String viewAllOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "admin/orders";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable("id") Long id,
                                    @RequestParam("status") OrderStatus status,
                                    RedirectAttributes redirectAttributes) {
        orderService.updateOrderStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Order #" + id + " status updated to " + status.getDisplayName());
        return "redirect:/admin/orders";
    }
}
