package com.ecom.config;

import com.ecom.model.*;
import com.ecom.repository.CartRepository;
import com.ecom.repository.CategoryRepository;
import com.ecom.repository.ProductRepository;
import com.ecom.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.ecom.ai.service.VectorSearchService vectorSearchService;

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           CartRepository cartRepository,
                           PasswordEncoder passwordEncoder,
                           @org.springframework.context.annotation.Lazy com.ecom.ai.service.VectorSearchService vectorSearchService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.passwordEncoder = passwordEncoder;
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return; // Data already initialized
        }

        // 1. Seed Categories
        Category electronics = new Category("Electronics", "electronics", "Laptops, high performance tablets and premium tech gear", "fa-laptop");
        Category audio = new Category("Audio & Hi-Fi", "audio", "Studio-grade wireless headphones, noise cancellation and soundbars", "fa-headphones");
        Category wearables = new Category("Smart Wearables", "wearables", "Fitness tracking smartwatches, bands and bio-sensing wearables", "fa-clock");
        Category homeOffice = new Category("Home & Workspace", "home-office", "Ergonomic furniture, mechanical keyboards and accessories", "fa-briefcase");
        categoryRepository.saveAll(List.of(electronics, audio, wearables, homeOffice));

        // 2. Seed Products
        Product p1 = new Product(
                "Apex Pro UltraBook 16\"",
                "apex-pro-ultrabook-16",
                "Next-generation 16-inch high performance laptop with Liquid Retina XDR display, 32GB unified memory, and 1TB ultra-fast NVMe storage. Designed for creators, engineers, and power users.",
                new BigDecimal("1899.99"),
                electronics,
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80",
                "LAP-APX-16",
                15
        );
        p1.setRating(4.9);
        p1.setReviewsCount(42);

        Product p2 = new Product(
                "AuraStudio ANC Wireless Headphones",
                "aurastudio-anc-wireless-headphones",
                "Industry-leading active noise cancellation with 40mm custom drivers, spatial audio tracking, ultra-soft memory foam earcups, and up to 45 hours of battery life on a single charge.",
                new BigDecimal("299.99"),
                audio,
                "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80",
                "AUD-AURA-01",
                20
        );
        p2.setRating(4.8);
        p2.setReviewsCount(118);

        Product p3 = new Product(
                "Horizon GPS Smartwatch Titanium",
                "horizon-gps-smartwatch-titanium",
                "Military-grade titanium casing with always-on sapphire crystal AMOLED display. Features ECG monitoring, dual-frequency GPS, multi-sport tracking, and 100m water resistance.",
                new BigDecimal("349.99"),
                wearables,
                "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80",
                "WEAR-HRZ-02",
                18
        );
        p3.setRating(4.7);
        p3.setReviewsCount(64);

        Product p4 = new Product(
                "KeyMaster Mechanical Keyboard (RGB)",
                "keymaster-mechanical-keyboard",
                "Custom hot-swappable mechanical switches, aircraft-grade aluminum top frame, sound-dampening foam, per-key RGB backlighting, and Bluetooth 5.2 multi-device connectivity.",
                new BigDecimal("139.99"),
                homeOffice,
                "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80",
                "ACC-KEY-03",
                4 // Low stock test case!
        );
        p4.setLowStockThreshold(5);
        p4.setRating(4.6);
        p4.setReviewsCount(33);

        Product p5 = new Product(
                "Curved UltraWide Monitor 34\" 165Hz",
                "curved-ultrawide-monitor-34",
                "Immersive 34-inch 1500R curved WQHD (3440x1440) monitor with 165Hz refresh rate, HDR400, USB-C 90W power delivery, and built-in KVM switch.",
                new BigDecimal("599.99"),
                electronics,
                "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80",
                "MON-UW-34",
                8
        );
        p5.setRating(4.9);
        p5.setReviewsCount(27);

        Product p6 = new Product(
                "ErgoFlow Vertical Wireless Mouse",
                "ergoflow-vertical-wireless-mouse",
                "Scientific 57-degree vertical grip that reduces wrist strain, 4000 DPI high-precision sensor, whisper-quiet clicks, and USB-C fast charging.",
                new BigDecimal("69.99"),
                homeOffice,
                "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=800&q=80",
                "ACC-MSE-04",
                25
        );
        p6.setRating(4.5);
        p6.setReviewsCount(89);

        Product p7 = new Product(
                "MagPower 3-in-1 Fast Wireless Dock",
                "magpower-3-in-1-fast-wireless-dock",
                "Premium weighted aluminum stand that simultaneously fast charges your phone, smartwatch, and wireless earbuds with certified magnetic alignment.",
                new BigDecimal("89.99"),
                electronics,
                "https://images.unsplash.com/photo-1622445262464-84b14e0745b1?auto=format&fit=crop&w=800&q=80",
                "CHG-MAG-05",
                0 // Out of Stock test case!
        );
        p7.setRating(4.4);
        p7.setReviewsCount(15);

        Product p8 = new Product(
                "SonicBoom High-Res Bookshelf Speakers",
                "sonicboom-high-res-bookshelf-speakers",
                "Pair of handcrafted wooden studio monitors featuring 1-inch silk dome tweeters, 5.25-inch aramid fiber woofers, optical/Bluetooth 5.0 inputs, and 120W peak power.",
                new BigDecimal("449.99"),
                audio,
                "https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=80",
                "AUD-SPK-06",
                7
        );
        p8.setRating(4.9);
        p8.setReviewsCount(52);

        List<Product> savedProducts = productRepository.saveAll(List.of(p1, p2, p3, p4, p5, p6, p7, p8));
        Product savedP2 = savedProducts.get(1);

        // 3. Seed Users
        // Admin
        User admin = new User(
                "admin",
                "admin@ecomstore.com",
                passwordEncoder.encode("admin123"),
                "Store Administrator",
                "+1 (555) 010-0001",
                Role.ROLE_ADMIN
        );
        userRepository.save(admin);
        cartRepository.save(new Cart(admin));

        // Customer: John Doe
        User john = new User(
                "john",
                "john@example.com",
                passwordEncoder.encode("password123"),
                "John Doe",
                "+1 (555) 234-5678",
                Role.ROLE_CUSTOMER
        );
        john.setDefaultAddress(new Address(
                "John Doe",
                "742 Evergreen Terrace",
                "Springfield",
                "Oregon",
                "97477",
                "United States",
                "+1 (555) 234-5678"
        ));
        userRepository.save(john);
        Cart johnCart = new Cart(john);
        // Pre-populate 1 item in John's cart for instant testability
        johnCart.addItem(savedP2, 1);
        cartRepository.save(johnCart);

        // Customer: Jane Smith
        User jane = new User(
                "jane",
                "jane@example.com",
                passwordEncoder.encode("password123"),
                "Jane Smith",
                "+1 (555) 987-6543",
                Role.ROLE_CUSTOMER
        );
        jane.setDefaultAddress(new Address(
                "Jane Smith",
                "1200 Market Street, Suite 400",
                "San Francisco",
                "California",
                "94102",
                "United States",
                "+1 (555) 987-6543"
        ));
        userRepository.save(jane);
        cartRepository.save(new Cart(jane));

        // 4. Initialize Vector Search Index
        vectorSearchService.refreshIndex();
    }
}
