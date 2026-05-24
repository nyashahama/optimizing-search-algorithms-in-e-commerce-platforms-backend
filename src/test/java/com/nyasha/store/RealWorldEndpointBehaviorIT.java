package com.nyasha.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.Role;
import com.nyasha.store.entities.Supplier;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.CategoryRepository;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.RoleRepository;
import com.nyasha.store.repositories.UserRepository;
import com.nyasha.store.repositories.SupplierRepository;
import com.nyasha.store.services.UserService;
import com.nyasha.store.utils.ProductIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RealWorldEndpointBehaviorIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductIndex productIndex;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void publicCatalogAndSearchEndpointsWorkWithRealCatalogData() throws Exception {
        String marker = "seed-" + UUID.randomUUID();
        Category category = seedCategory("Category " + marker);
        Product product = seedProduct("Catalog Product " + marker, "SKU-" + UUID.randomUUID(), 59.99, category);
        seedInventory(product, 5);

        JsonNode allProducts = readTree(mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(hasMatch(allProducts, entry -> entry.path("productId").asLong() == product.getProductId())).isTrue();

        JsonNode productById = readTree(mockMvc.perform(get("/api/products/{id}", product.getProductId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(productById.path("name").asText()).isEqualTo(product.getName());

        JsonNode search = readTree(mockMvc.perform(get("/api/search").param("q", "Catalog"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(search.path("result").path("products").isArray()).isTrue();
        assertThat(hasMatch(search.path("result").path("products"),
                item -> product.getName().equals(item.path("name").asText())
        )).isTrue();

        JsonNode autocomplete = readTree(mockMvc.perform(get("/api/search/autocomplete").param("q", marker.substring(0, 4)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(autocomplete.isArray()).isTrue();

        JsonNode compare = readTree(mockMvc.perform(get("/api/search/compare").param("q", marker))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(compare.path("results").isArray()).isTrue();
        assertThat(hasMatch(compare.path("results"), result -> "IN_MEMORY".equals(result.path("engine").asText())
                && result.path("products").isArray()
                && result.path("products").size() > 0)).isTrue();

        JsonNode productSearch = readTree(mockMvc.perform(get("/api/products/search").param("query", "Catalog"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(productSearch.isArray()).isTrue();
        assertThat(hasMatch(productSearch, item -> product.getName().equals(item.path("name").asText()))).isTrue();

        JsonNode productAutocomplete = readTree(mockMvc.perform(get("/api/products/autocomplete").param("prefix", "Cat"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(productAutocomplete.isArray()).isTrue();

        JsonNode productReviews = readTree(mockMvc.perform(get("/api/reviews/products/{productId}", product.getProductId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(productReviews.isArray()).isTrue();

        JsonNode allCategories = readTree(mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(hasMatch(allCategories, entry -> entry.path("categoryId").asLong() == category.getCategoryId())).isTrue();

        JsonNode productsByCategory = readTree(mockMvc.perform(get("/api/products/category/{id}", category.getCategoryId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(hasMatch(productsByCategory, entry -> entry.path("productId").asLong() == product.getProductId())).isTrue();
    }

    @Test
    void authenticatedCustomerCanUseCartCheckoutWishlistReviewsAndPayments() throws Exception {
        String marker = "buyer-" + UUID.randomUUID();
        Product product = seedProduct("Checkout Product " + marker, "SKB-" + UUID.randomUUID(), 79.99, null);
        seedInventory(product, 10);

        String email = marker + "@example.com";
        String password = randomPassword();
        registerUser(email, password);
        long addressId = createAddress(email, password);

        JsonNode cart = addCartItem(email, password, product.getProductId(), 2);
        assertThat(cart.path("cartItems").isArray()).isTrue();
        assertThat(cart.path("cartItems").size()).isGreaterThan(0);

        long cartItemId = cart.path("cartItems").get(0).path("cartItemId").asLong();
        cart = updateCartQuantity(email, password, cartItemId, 3);
        assertThat(cart.path("cartItems").get(0).path("quantity").asInt()).isEqualTo(3);

        JsonNode preview = checkoutPreview(email, password, addressId);
        assertThat(preview.path("subtotal").asDouble()).isGreaterThan(0.0);

        String idempotencyKey = "idemp-" + UUID.randomUUID();
        JsonNode confirmation = confirmCheckout(email, password, addressId, idempotencyKey);
        long orderId = confirmation.path("orderId").asLong();
        assertThat(orderId).isPositive();
        assertThat(confirmation.path("orderStatus").asText()).isEqualTo("PAID");

        JsonNode orders = readTree(mockMvc.perform(get("/api/orders/me").with(httpBasic(email, password)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(orders.isArray()).isTrue();
        assertThat(hasMatch(orders, entry -> entry.path("orderId").asLong() == orderId)).isTrue();

        JsonNode order = readTree(mockMvc.perform(get("/api/orders/{id}", orderId).with(httpBasic(email, password)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(order.path("status").asText()).isEqualTo("PAID");

        JsonNode payment = readTree(mockMvc.perform(get("/api/payments/orders/{orderId}", orderId).with(httpBasic(email, password)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(payment.path("paymentId").asLong()).isPositive();

        JsonNode cancelled = readTree(mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .with(httpBasic(email, password)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");

        JsonNode wishlist = readTree(mockMvc.perform(get("/api/wishlists/me").with(httpBasic(email, password)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(wishlist.path("wishlistItems").isArray()).isTrue();

        JsonNode wishlistAfterAdd = addWishlistItem(email, password, product.getProductId());
        assertThat(wishlistAfterAdd.path("wishlistItems").size()).isEqualTo(1);

        long wishlistItemId = wishlistAfterAdd.path("wishlistItems").get(0).path("wishlistItemId").asLong();
        mockMvc.perform(delete("/api/wishlists/me/items/{itemId}", wishlistItemId)
                        .with(httpBasic(email, password)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/reviews")
                        .with(httpBasic(email, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"rating\":4,\"comment\":\"Great value.\"}".formatted(product.getProductId())))
                .andExpect(status().isOk());

        JsonNode publicReviews = readTree(mockMvc.perform(get("/api/reviews/products/{productId}", product.getProductId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(publicReviews.isArray()).isTrue();
        assertThat(hasMatch(publicReviews, entry -> "Great value.".equals(entry.path("comment").asText()))).isTrue();
    }

    @Test
    void adminCanMutateCatalogAndAccessIndexEndpoints() throws Exception {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = randomPassword();
        seedAdminUser(adminEmail, adminPassword);

        MvcResult categoryCreate = mockMvc.perform(post("/api/categories")
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations Category\"}"))
                .andExpect(status().isOk())
                .andReturn();

        long categoryId = readTree(categoryCreate).path("categoryId").asLong();
        assertThat(categoryId).isPositive();

        String sku = "OPS-" + UUID.randomUUID();
        MvcResult productCreate = mockMvc.perform(post("/api/products")
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ops Product\",\"sku\":\"%s\",\"basePrice\":49.99}".formatted(sku)))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = readTree(productCreate).path("productId").asLong();
        assertThat(productId).isPositive();

        mockMvc.perform(get("/api/index/status").with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("openSearchDocumentCount").isNumber());

        mockMvc.perform(post("/api/index/rebuild").with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/categories/{id}", categoryId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("name").value("Operations Category"));
        assertThat(categoryRepository.existsById(categoryId)).isTrue();

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("name").value("Ops Product"));
    }

    @Test
    void adminCanManageSuppliersAndInventory() throws Exception {
        String marker = "admin-" + UUID.randomUUID();
        String adminEmail = marker + "@example.com";
        String adminPassword = randomPassword();
        seedAdminUser(adminEmail, adminPassword);

        MvcResult supplierCreate = mockMvc.perform(post("/api/suppliers")
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "contactInfo":"%s",
                                  "address":"%s"
                                }
                                """.formatted("Supplier " + marker, "support@" + marker + ".test", "123 Commerce Ave"))
                )
                .andExpect(status().isCreated())
                .andReturn();

        long supplierId = readTree(supplierCreate).path("supplierId").asLong();
        assertThat(supplierId).isPositive();

        mockMvc.perform(get("/api/suppliers").with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk());

        JsonNode supplier = readTree(mockMvc.perform(get("/api/suppliers/{id}", supplierId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(supplier.path("name").asText()).contains(marker);

        String updatedSupplierName = "Updated Supplier " + marker;
        JsonNode updatedSupplier = readTree(mockMvc.perform(put("/api/suppliers/{id}", supplierId)
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "contactInfo":"Updated contact",
                                  "address":"Updated address"
                                }
                                """.formatted(updatedSupplierName))
                )
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updatedSupplier.path("name").asText()).isEqualTo(updatedSupplierName);

        Supplier supplierEntity = supplierRepository.findById(supplierId).orElseThrow();
        Product linkedProduct = seedProduct(
                "Supplier Bound Product " + marker,
                "SP-" + UUID.randomUUID(),
                120.0,
                null,
                supplierEntity
        );
        seedInventory(linkedProduct, 4);

        MvcResult orphanSupplierCreate = mockMvc.perform(post("/api/suppliers")
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "contactInfo":"%s",
                                  "address":"%s"
                                }
                                """.formatted("Orphan " + marker, "ops@" + marker + ".test", "456 Side St")
                        )
                )
                .andExpect(status().isCreated())
                .andReturn();

        long orphanSupplierId = readTree(orphanSupplierCreate).path("supplierId").asLong();
        assertThat(orphanSupplierId).isPositive();
        assertThat(readTree(orphanSupplierCreate).path("supplierId").asLong()).isEqualTo(orphanSupplierId);

        mockMvc.perform(delete("/api/suppliers/{id}", supplierId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/suppliers/{id}", orphanSupplierId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/suppliers/{id}", orphanSupplierId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isBadRequest());

        Product inventoryProduct = seedProduct("Inventory Product " + marker, "IP-" + UUID.randomUUID(), 49.99, null);
        seedInventory(inventoryProduct, 2);

        JsonNode lowStockBefore = readTree(mockMvc.perform(get("/api/inventory/low-stock")
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(hasMatch(lowStockBefore, item -> item.path("productId").asLong() == inventoryProduct.getProductId()))
                .isTrue();

        JsonNode upsertedInventory = readTree(mockMvc.perform(put("/api/inventory/{id}", inventoryProduct.getProductId())
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 12,
                                  "location": "main-dc",
                                  "reorderThreshold": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(upsertedInventory.path("quantity").asInt()).isEqualTo(12);
        assertThat(upsertedInventory.path("reorderThreshold").asInt()).isEqualTo(4);

        JsonNode adjustedInventory = readTree(mockMvc.perform(patch("/api/inventory/{id}/adjust", inventoryProduct.getProductId())
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "delta": -3
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(adjustedInventory.path("quantity").asInt()).isEqualTo(9);

        mockMvc.perform(get("/api/inventory/{id}", inventoryProduct.getProductId())
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("quantity").value(9));
    }

    @Test
    void adminCanRejectOrderReturns() throws Exception {
        String buyerEmail = "buyer-" + UUID.randomUUID() + "@example.com";
        String buyerPassword = randomPassword();
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = randomPassword();
        seedAdminUser(adminEmail, adminPassword);

        Product product = seedProduct("Return Candidate " + UUID.randomUUID(), "RC-" + UUID.randomUUID(), 79.99, null);
        seedInventory(product, 8);

        registerUser(buyerEmail, buyerPassword);
        long addressId = createAddress(buyerEmail, buyerPassword);
        addCartItem(buyerEmail, buyerPassword, product.getProductId(), 1);
        confirmCheckout(buyerEmail, buyerPassword, addressId, "idempotency-" + UUID.randomUUID());

        JsonNode order = readTree(mockMvc.perform(get("/api/orders/me")
                        .with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isOk())
                .andReturn())
                .get(0);
        long orderId = order.path("orderId").asLong();
        long orderItemId = order.path("items").get(0).path("orderItemId").asLong();

        JsonNode packed = readTree(mockMvc.perform(post("/api/orders/{id}/pack", orderId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(packed.path("status").asText()).isEqualTo("PACKED");

        mockMvc.perform(post("/api/orders/{id}/ship", orderId)
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNumber":"%s","carrier":"Fast Logistics"}
                                """.formatted("TRACK-" + UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/delivered", orderId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk());

        JsonNode returnCreate = mockMvc.perform(post("/api/returns/{orderId}", orderId)
                        .with(httpBasic(buyerEmail, buyerPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId":%d,"reason":"wrong size"}
                                """.formatted(orderItemId)))
                .andExpect(status().isOk())
                .andReturn();
        long returnId = readTree(returnCreate).path("returnId").asLong();
        assertThat(returnId).isPositive();

        mockMvc.perform(post("/api/returns/{returnId}/reject", returnId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk());

        JsonNode returnsAfterReject = readTree(mockMvc.perform(get("/api/returns/me").with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(hasMatch(returnsAfterReject, entry -> entry.path("returnId").asLong() == returnId
                && "REJECTED".equals(entry.path("status").asText()))).isTrue();
    }

    @Test
    void adminAndBuyerCanExecuteOrderReturnLifecycle() throws Exception {
        String buyerEmail = "buyer-" + UUID.randomUUID() + "@example.com";
        String buyerPassword = randomPassword();
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = randomPassword();
        seedAdminUser(adminEmail, adminPassword);

        Product product = seedProduct("Order Return Product " + UUID.randomUUID(), "OR-" + UUID.randomUUID(), 99.99, null);
        seedInventory(product, 5);

        registerUser(buyerEmail, buyerPassword);
        long addressId = createAddress(buyerEmail, buyerPassword);
        addCartItem(buyerEmail, buyerPassword, product.getProductId(), 2);
        checkoutPreview(buyerEmail, buyerPassword, addressId);

        String confirmIdempotencyKey = "order-lifecycle-" + UUID.randomUUID();
        JsonNode confirmation = confirmCheckout(
                buyerEmail,
                buyerPassword,
                addressId,
                confirmIdempotencyKey,
                0.0,
                0.0
        );
        long orderId = confirmation.path("orderId").asLong();
        assertThat(orderId).isPositive();
        assertThat(confirmation.path("orderStatus").asText()).isEqualTo("PAID");

        JsonNode orderBefore = readTree(mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isOk())
                .andReturn());
        long orderItemId = orderBefore.path("items").get(0).path("orderItemId").asLong();

        JsonNode paymentAtStart = readTree(mockMvc.perform(get("/api/payments/orders/{orderId}", orderId)
                        .with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(paymentAtStart.path("status").asText()).isEqualTo("CAPTURED");

        JsonNode packed = readTree(mockMvc.perform(post("/api/orders/{id}/pack", orderId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(packed.path("status").asText()).isEqualTo("PACKED");

        JsonNode shipped = readTree(mockMvc.perform(post("/api/orders/{id}/ship", orderId)
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber":"%s",
                                  "carrier":"Fast Logistics"
                                }
                                """.formatted("TRACK-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(shipped.path("status").asText()).isEqualTo("SHIPPED");

        JsonNode delivered = readTree(mockMvc.perform(post("/api/orders/{id}/delivered", orderId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(delivered.path("status").asText()).isEqualTo("DELIVERED");

        MvcResult returnCreate = mockMvc.perform(post("/api/returns/{orderId}", orderId)
                        .with(httpBasic(buyerEmail, buyerPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderItemId":%d,"reason":"defect"}
                                """.formatted(orderItemId)))
                .andExpect(status().isOk())
                .andReturn();
        long returnId = readTree(returnCreate).path("returnId").asLong();
        assertThat(returnId).isPositive();

        mockMvc.perform(post("/api/returns/{returnId}/approve", returnId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk());

        JsonNode returnsAfterApprove = readTree(mockMvc.perform(get("/api/returns/me")
                        .with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(hasMatch(returnsAfterApprove,
                item -> item.path("returnId").asLong() == returnId
                        && "APPROVED".equals(item.path("status").asText())
        )).isTrue();

        String refundKey = "refund-" + UUID.randomUUID();
        JsonNode refunded = readTree(mockMvc.perform(post("/api/returns/{returnId}/refund", returnId)
                        .with(httpBasic(buyerEmail, buyerPassword))
                        .header("Idempotency-Key", refundKey))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(refunded.path("status").asText()).isEqualTo("REFUNDED");

        JsonNode orderAfterRefund = readTree(mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(orderAfterRefund.path("status").asText()).isEqualTo("RETURNED");

        JsonNode paymentAfterRefund = readTree(mockMvc.perform(get("/api/payments/orders/{orderId}", orderId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(paymentAfterRefund.path("status").asText()).isEqualTo("REFUNDED");

        JsonNode finalInventory = readTree(mockMvc.perform(get("/api/inventory/{productId}", product.getProductId())
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(finalInventory.path("quantity").asInt()).isEqualTo(5);
    }

    @Test
    void adminCanCaptureAndRefundPaymentsForOrders() throws Exception {
        String buyerEmail = "buyer-" + UUID.randomUUID() + "@example.com";
        String buyerPassword = randomPassword();
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = randomPassword();
        seedAdminUser(adminEmail, adminPassword);

        Product product = seedProduct("Payment Ops Product " + UUID.randomUUID(), "PO-" + UUID.randomUUID(), 129.99, null);
        seedInventory(product, 4);

        registerUser(buyerEmail, buyerPassword);
        long addressId = createAddress(buyerEmail, buyerPassword);
        addCartItem(buyerEmail, buyerPassword, product.getProductId(), 1);
        JsonNode confirmation = confirmCheckout(
                buyerEmail,
                buyerPassword,
                addressId,
                "capture-refund-" + UUID.randomUUID(),
                0.0,
                0.0
        );
        long orderId = confirmation.path("orderId").asLong();
        assertThat(orderId).isPositive();

        JsonNode capturedPayment = readTree(mockMvc.perform(post("/api/payments/orders/{orderId}/capture", orderId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(capturedPayment.path("status").asText()).isEqualTo("CAPTURED");

        mockMvc.perform(post("/api/payments/orders/{orderId}/capture", orderId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk());

        JsonNode refundedPayment = readTree(mockMvc.perform(post("/api/payments/orders/{orderId}/refund", orderId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(refundedPayment.path("status").asText()).isEqualTo("REFUNDED");

        mockMvc.perform(post("/api/payments/orders/{orderId}/refund", orderId)
                        .with(httpBasic(buyerEmail, buyerPassword)))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiOperationsEndpointIsProtectedAndReturnsStatus() throws Exception {
        String userEmail = "ops-user-" + UUID.randomUUID() + "@example.com";
        String userPassword = randomPassword();
        String adminEmail = "ops-admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = randomPassword();

        seedAdminUser(adminEmail, adminPassword);
        registerUser(userEmail, userPassword);

        mockMvc.perform(get("/api/ops/status"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/ops/status").with(httpBasic(userEmail, userPassword)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ops/status").with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("overallStatus").isNotEmpty());
    }

    @Test
    void adminCanManageUsersAndRunBenchmarks() throws Exception {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = randomPassword();
        seedAdminUser(adminEmail, adminPassword);

        String targetEmail = "managed-" + UUID.randomUUID() + "@example.com";
        String targetPassword = randomPassword();
        JsonNode createdUser = registerUserAndRead(targetEmail, targetPassword, "Managed User");
        long targetUserId = createdUser.path("userId").asLong();
        assertThat(targetUserId).isPositive();

        JsonNode users = readTree(mockMvc.perform(get("/users").with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(hasMatch(users, item -> item.path("userId").asLong() == targetUserId)).isTrue();

        JsonNode userLookup = readTree(mockMvc.perform(get("/users/{id}", targetUserId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(userLookup.path("email").asText()).isEqualTo(targetEmail);

        JsonNode userSearch = readTree(mockMvc.perform(get("/users/search")
                        .param("query", "Managed")
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(hasMatch(userSearch, item -> item.path("userId").asLong() == targetUserId)).isTrue();

        JsonNode updatedUser = readTree(mockMvc.perform(put("/users/{id}", targetUserId)
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Managed User Updated",
                                  "email":"%s",
                                  "hashedPassword":"%s"
                                }
                                """.formatted("updated-" + targetEmail, targetPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updatedUser.path("email").asText()).isEqualTo("updated-" + targetEmail);

        JsonNode benchmarkStart = readTree(mockMvc.perform(post("/api/benchmarks/runs")
                        .with(httpBasic(adminEmail, adminPassword))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":1}"))
                .andExpect(status().isAccepted())
                .andReturn());
        long runId = benchmarkStart.path("runId").asLong();
        assertThat(runId).isPositive();
        assertThat(benchmarkStart.path("queryCount").asInt()).isPositive();

        JsonNode benchmarkRun = readTree(mockMvc.perform(get("/api/benchmarks/runs/{runId}", runId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(benchmarkRun.path("runId").asLong()).isEqualTo(runId);
        assertThat(benchmarkRun.path("querySetName").asText()).isNotBlank();
        assertThat(benchmarkRun.path("totalQueries").asInt()).isGreaterThan(0);

        JsonNode benchmarkResults = readTree(mockMvc.perform(get("/api/benchmarks/runs/{runId}/results", runId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(benchmarkResults.isArray()).isTrue();

        mockMvc.perform(get("/api/benchmarks/runs/{runId}/artifacts/missing.csv", runId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/users/{id}", targetUserId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/users/{id}", targetUserId).with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isNotFound());

        waitForBenchmarkRunCompletion(benchmarkStart.path("runId").asLong(), adminEmail, adminPassword);

        mockMvc.perform(get("/api/benchmarks/runs/{runId}/artifacts/summary.md", runId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("Run ID"));
        mockMvc.perform(get("/api/benchmarks/runs/{runId}/artifacts/results.json", runId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("\"status\""));
        mockMvc.perform(get("/api/benchmarks/runs/{runId}/artifacts/latency.csv", runId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).startsWith("queryText,engine,latencyMs"));
        mockMvc.perform(get("/api/benchmarks/runs/{runId}/artifacts/relevance.csv", runId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).startsWith("queryText,engine,precisionAtK"));
        mockMvc.perform(get("/api/benchmarks/runs/{runId}/artifacts/missing.txt", runId)
                        .with(httpBasic(adminEmail, adminPassword)))
                .andExpect(status().isBadRequest());
    }

    private Category seedCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private Product seedProduct(String name, String sku, double basePrice, Category category) {
        return seedProduct(name, sku, basePrice, category, null);
    }

    private Product seedProduct(String name, String sku, double basePrice, Category category, Supplier supplier) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Integration seed");
        product.setSku(sku);
        product.setBasePrice(basePrice);
        product.setSupplier(supplier);

        if (category != null) {
            Set<Category> categories = new HashSet<>();
            categories.add(category);
            product.setCategories(categories);
        }

        Product saved = productRepository.save(product);
        productIndex.insert(saved);
        return saved;
    }

    private void seedInventory(Product product, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setVariant(null);
        inventory.setQuantity(quantity);
        inventory.setLocation("main");
        inventory.setReorderThreshold(2);
        inventoryRepository.save(inventory);
    }

    private void seedAdminUser(String email, String password) {
        User user = new User();
        user.setName("Endpoint Operator");
        user.setEmail(email);
        user.setHashedPassword(password);

        User admin = userService.createUser(user);
        Role adminRole = roleRepository.findByNameIgnoreCase("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Missing ADMIN role"));
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private JsonNode registerUserAndRead(String email, String password, String name) throws Exception {
        return readTree(mockMvc.perform(
                post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"hashedPassword\":\"%s\"}".formatted(name, email, password))
        )
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\n                                        \"name\": \"Integration Buyer\",\n                                        \"email\": \"%s\",\n                                        \"hashedPassword\": \"%s\"\n                                    }".formatted(email, password))
                )
                .andExpect(status().isCreated());
    }

    private long createAddress(String email, String password) throws Exception {
        MvcResult response = mockMvc.perform(
                        post("/api/addresses/me")
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\n                                        \"street\": \"123 Store St\",\n                                        \"city\": \"Springfield\",\n                                        \"state\": \"NA\",\n                                        \"zip\": \"12345\",\n                                        \"country\": \"USA\"\n                                    }")
                )
                .andExpect(status().isCreated())
                .andReturn();

        return readTree(response).path("addressId").asLong();
    }

    private JsonNode addCartItem(String email, String password, Long productId, int quantity) throws Exception {
        MvcResult response = mockMvc.perform(
                        post("/api/carts/me/items")
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(response);
    }

    private JsonNode updateCartQuantity(String email, String password, long cartItemId, int quantity) throws Exception {
        MvcResult response = mockMvc.perform(
                        patch("/api/carts/me/items/{itemId}", cartItemId)
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":%d}".formatted(quantity))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(response);
    }

    private JsonNode checkoutPreview(String email, String password, long addressId) throws Exception {
        MvcResult response = mockMvc.perform(
                        post("/api/checkouts/preview")
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"shippingAddressId\":%d}".formatted(addressId))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(response);
    }

    private JsonNode confirmCheckout(String email, String password, long addressId, String idempotencyKey) throws Exception {
        MvcResult response = mockMvc.perform(
                        post("/api/checkouts/confirm")
                                .with(httpBasic(email, password))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"shippingAddressId\":%d,\"paymentMethod\":\"SIMULATED\"}".formatted(addressId))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(response);
    }

    private JsonNode confirmCheckout(String email, String password, long addressId, String idempotencyKey, Double shippingCost, Double taxRate) throws Exception {
        String body = (shippingCost == null && taxRate == null)
                ? "{\"shippingAddressId\":%d,\"paymentMethod\":\"SIMULATED\"}".formatted(addressId)
                : "{\"shippingAddressId\":%d,\"paymentMethod\":\"SIMULATED\",\"shippingCost\":%s,\"taxRate\":%s}"
                        .formatted(addressId, shippingCost, taxRate);

        MvcResult response = mockMvc.perform(
                        post("/api/checkouts/confirm")
                                .with(httpBasic(email, password))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(response);
    }

    private JsonNode addWishlistItem(String email, String password, long productId) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/wishlists/me/items")
                        .with(httpBasic(email, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(productId)))
                .andExpect(status().isOk())
                .andReturn();
        return readTree(response);
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean hasMatch(JsonNode array, Predicate<JsonNode> predicate) {
        if (array == null || !array.isArray()) {
            return false;
        }
        return StreamSupport.stream(array.spliterator(), false).anyMatch(predicate);
    }

    private String randomPassword() {
        return "pw-" + UUID.randomUUID().toString().replace("-", "");
    }

    private void waitForBenchmarkRunCompletion(long runId, String adminEmail, String adminPassword) throws Exception {
        int attempts = 0;
        while (attempts < 40) {
            attempts++;
            JsonNode run = readTree(mockMvc.perform(get("/api/benchmarks/runs/{runId}", runId)
                            .with(httpBasic(adminEmail, adminPassword)))
                    .andExpect(status().isOk())
                    .andReturn());

            String status = run.path("status").asText();
            if ("COMPLETED".equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                throw new IllegalStateException("Benchmark run failed while waiting for completion: " + runId);
            }

            Thread.sleep(250L);
        }
        throw new IllegalStateException("Timed out waiting for benchmark completion: " + runId);
    }
}
