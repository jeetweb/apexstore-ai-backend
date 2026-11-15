# 🛒 ApexStore - Enterprise E-Commerce Platform (Java 21)

A full-featured, functional, production-ready E-Commerce platform developed with **Java 21**, **Spring Boot 3**, **Spring Security 6**, **Spring Data JPA / Hibernate**, **H2 Database**, and a responsive modern frontend powered by **Thymeleaf** and **Tailwind CSS**.

---

## 🌟 Key Features

### 1. 🔐 Authentication & Role-Based Access Control
- **Spring Security 6 with BCrypt** password hashing.
- Role separation: `ROLE_CUSTOMER` and `ROLE_ADMIN`.
- Session management with CSRF protection, secure cookie handling, and route guarding.
- **1-Click Quick Login** buttons on the login page for effortless evaluation.

### 2. 🤖 Autonomous AI Agent with Deterministic Tool Calling
- Embedded **ApexStore AI Concierge** with function calling over backend services:
  - `check_inventory`: Real-time warehouse stock and pricing by SKU/title.
  - `track_order`: Live OMS delivery and courier tracking code verification.
  - `search_catalog`: Intent-based semantic retrieval.
  - `add_to_cart`: Direct conversational cart manipulation.
- Built-in deterministic guardrails prevent LLM hallucinations or unauthorized price/discount manipulations.
- Interactive floating chat UI on the storefront with quick action chips.

### 3. 🧠 Hybrid Vector Semantic Search (128-Dim Embeddings)
- Dense vector representations over product attributes with cosine similarity ranking.
- Natural language discovery: Translates intents (*"lightweight laptop for video editing under $2000"*) into scored product recommendations.
- Automatic price ceiling extraction from conversational queries.
- Hybrid scoring combines cosine semantic similarity with exact keyword/SKU boosts.

### 4. 🛍️ Dynamic Product Catalog & Concurrency Protection
- **Optimistic Locking (`@Version`)** on `Product` entities preventing race conditions and overselling during simultaneous checkout bursts.
- Real-time stock status badges:
  - 🟢 **Healthy Stock**
  - 🟡 **Low Stock Alert** (calculated dynamically against product threshold)
  - 🔴 **Out of Stock** (disables purchasing buttons automatically)

### 5. 🛒 Cart Management
- Persistent database-backed shopping cart per user.
- Add items, increment/decrement quantities, and remove items with instant total calculations.
- Live progress bar towards **FREE Shipping** (threshold: \$50.00).
- Subtotal, 8% tax calculation, and Standard (\$5.99) / Express (\$19.99) shipping options.

### 6. 💳 Digital Payment Processing
- Simulated real-world digital payment gateways with transaction ID generation (`TXN-...`) and gateway references (`GW-...`).
- Supported digital payment rails:
  - **Credit / Debit Cards** (Visa, Mastercard, Amex with format & expiry validation).
  - **UPI** (Unified Payments Interface with Virtual Payment Address verification).
  - **Digital Wallets** (Apple Pay, Google Pay, PayPal).
- Built-in test simulation hooks:
  - Card ending with `0000` or CVV `999` &rarr; Simulates bank card decline.
  - UPI ID `fail@upi` &rarr; Simulates UPI PIN validation failure.

### 7. 📦 Real-Time Inventory Control & Checkout Flow
- **Atomic Checkout**: Verifies stock availability, decrements inventory, processes digital payment, creates order with unique tracking code (`TRK-EXP-...`), and empties the customer's cart in a single transactional unit (`@Transactional`).
- Stock guard prevents adding more items than currently in stock.

### 8. 🛠️ Admin Control Center
- **Executive Dashboard** (`/admin`): Real-time revenue metrics, order counters, catalog health, low-stock watchlist, and recent orders.
- **Inventory Manager** (`/admin/inventory`): Filter by low-stock items, inline stock quantity updating, threshold adjustments, and quick restock buttons (`+5`, `+10`, `+25`).
- **Product Management** (`/admin/products/new`, `/admin/products/edit/{id}`): Create and modify products with custom SKU, category, pricing, and stock.
- **Order Fulfillment** (`/admin/orders`): Track all customer orders, inspect digital payment methods, and transition order lifecycle statuses (`PAID` &rarr; `PROCESSING` &rarr; `SHIPPED` &rarr; `DELIVERED`).

### 9. 📖 REST API & Diagnostic Consoles
- **Interactive Swagger OpenAPI 3 UI**: Test all catalog, cart, inventory, AI agent (`/api/ai/chat`), and semantic search (`/api/ai/search`) endpoints at `/swagger-ui.html`.
- **H2 In-Memory SQL Console**: Direct SQL access to tables at `/h2-console` (`jdbc:h2:mem:ecomdb`, user: `sa`, password: empty).

---

## 🚀 Quick Start Guide

### Prerequisites
- **Java 21** (`openjdk@21` or compatible JDK 21)
- **Maven 3.8+**

### 1. Run with Maven
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home mvn spring-boot:run
```
*(Or simply `mvn spring-boot:run` if your default `JAVA_HOME` points to Java 21)*

### 2. Or Run the Pre-Packaged JAR
```bash
java -jar target/ecom-store-1.0.0-SNAPSHOT.jar
```

### 3. Open in Browser
Visit [http://localhost:8080](http://localhost:8080)

---

## 🔑 Pre-Configured Test Accounts

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Customer** | `john` | `password123` | Pre-populated with default address & cart item |
| **Customer** | `jane` | `password123` | Pre-configured customer account |
| **Admin** | `admin` | `admin123` | Full access to `/admin` dashboard & inventory |

*(You can also click the quick buttons directly on `/login` or register a new customer account at `/register`)*

---

## 🧪 Digital Payment Test Credentials

When testing the checkout flow at `/checkout`:

| Payment Method | Input Values | Result |
| :--- | :--- | :--- |
| **Credit Card (Success)** | Card: `4242 4242 4242 4242`<br>Exp: `12/28`<br>CVV: `123` | ✅ **Payment Confirmed** &rarr; Order placed with tracking code |
| **Credit Card (Decline Test)** | Card: `4111 1111 1111 0000`<br>Exp: `12/28`<br>CVV: `123` | ❌ **Declined by Bank** &rarr; Inventory preserved |
| **UPI (Success)** | UPI ID: `john@okhdfcbank` | ✅ **Instant UPI Settlement** |
| **UPI (Failure Test)** | UPI ID: `fail@upi` | ❌ **UPI PIN validation failed** |
| **Digital Wallet** | Apple Pay / Google Pay / PayPal | ✅ **One-touch biometric settlement** |

---

## 🧪 Automated Test Suite

Run the full suite of integration and web MVC tests:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home mvn test
```

### Test Coverage Highlights:
- `EcomFlowIntegrationTest`:
  - Customer registration & password hashing
  - Cart operations (adding, modifying quantities, threshold shipping)
  - Inventory guard against stock overselling
  - Digital payment execution across Card, UPI, and Wallet
  - Payment decline error handling & state preservation
  - Complete atomic checkout with stock decrement and cart clearing
- `WebControllerTest`:
  - Route accessibility and security access rules
  - Customer cart authorization
  - Admin dashboard & inventory authorization checks
  - REST API endpoint contracts (`/api/products`, `/api/inventory/stats`)

---

## 🏛️ Project Architecture

```
com.ecom
├── EcomApplication.java            # Spring Boot main class
├── config/
│   ├── DataInitializer.java        # Pre-seeds categories, products, stock, users
│   └── SecurityConfig.java         # Spring Security 6 filter chain & auth
├── controller/
│   ├── HomeController.java         # Home page & search landing
│   ├── ProductController.java      # Catalog & detailed product views
│   ├── CartController.java         # Add to cart, quantity updates, cart view
│   ├── CheckoutController.java     # Checkout form, order placement, success receipt
│   ├── OrderController.java        # User order history & order details
│   ├── AuthController.java         # Login, register, profile
│   ├── AdminController.java        # Inventory manager, product editor, order fulfillments
│   ├── GlobalControllerAdvice.java # Shared cart count, categories, auth context
│   └── api/
│       └── ApiController.java      # REST endpoints for Catalog, Cart, Inventory
├── dto/                            # Data Transfer Objects (Checkout, Product, Register, etc.)
├── exception/                      # Custom business exceptions (InsufficientStock, PaymentFailed)
├── model/                          # JPA Entities (User, Product, Category, Cart, Order, Payment)
├── repository/                     # Spring Data JPA repositories
└── service/
    ├── ProductService.java         # Catalog queries & product lifecycle
    ├── CartService.java            # Cart computations & operations
    ├── InventoryService.java       # Atomic stock decrements, low-stock checks
    ├── PaymentService.java         # Digital payment simulation & validation
    ├── OrderService.java           # Order placement & fulfillment
    └── UserService.java            # User registration & lookup
```
