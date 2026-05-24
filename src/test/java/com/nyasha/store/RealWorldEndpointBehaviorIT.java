package com.nyasha.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.Role;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.CategoryRepository;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.RoleRepository;
import com.nyasha.store.repositories.UserRepository;
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

    private Category seedCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private Product seedProduct(String name, String sku, double basePrice, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Integration seed");
        product.setSku(sku);
        product.setBasePrice(basePrice);

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
}
