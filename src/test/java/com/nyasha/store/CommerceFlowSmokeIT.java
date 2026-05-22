package com.nyasha.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.utils.ProductIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.function.Predicate;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommerceFlowSmokeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductIndex productIndex;

    @Test
    void fullCommerceFlowCheckoutAndSearchCompare() throws Exception {
        Product product = seedProduct("Wireless Mouse " + UUID.randomUUID(), "WM-" + UUID.randomUUID(), 49.99);
        seedInventory(product, 20);

        String email = "buyer+" + UUID.randomUUID() + "@example.com";
        String password = randomPassword();

        registerUser(email, password);
        long addressId = createAddress(email, password);

        addItemToCart(email, password, product.getProductId(), 2);

        JsonNode preview = checkoutPreview(email, password, addressId);
        assertThat(preview.path("totalAmount").asDouble()).isGreaterThan(0.0);

        String idempotencyKey = "checkout-" + UUID.randomUUID();
        JsonNode confirmation = confirmCheckout(email, password, addressId, idempotencyKey);
        long orderId = confirmation.path("orderId").asLong();

        assertThat(orderId).isPositive();
        assertThat(confirmation.path("orderStatus").asText()).isEqualTo("PAID");
        assertThat(confirmation.path("paymentStatus").asText()).isEqualTo("CAPTURED");

        JsonNode myOrders = getMyOrders(email, password);
        assertThat(myOrders.isArray()).isTrue();
        assertThat(hasMatch(myOrders, order -> order.path("orderId").asLong() == orderId)).isTrue();

        JsonNode compare = searchCompare(email, password, "wireless");
        assertThat(compare.path("results").isArray()).isTrue();
        assertThat(hasMatch(compare.path("results"), result -> "IN_MEMORY".equals(result.path("engine").asText()))).isTrue();
        assertThat(hasMatch(
                compare.path("results"),
                result -> "IN_MEMORY".equals(result.path("engine").asText())
                        && result.path("products").isArray()
                        && result.path("products").size() > 0
        )).isTrue();
    }

    private Product seedProduct(String name, String sku, double basePrice) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Wireless accessory for smoke-test order flow");
        product.setSku(sku);
        product.setBasePrice(basePrice);

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

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Smoke Test Buyer",
                                          "email": "%s",
                                          "hashedPassword": "%s"
                                        }
                                        """.formatted(email, password))
                )
                .andExpect(status().isCreated());
    }

    private long createAddress(String email, String password) throws Exception {
        MvcResult addressResponse = mockMvc.perform(
                        post("/api/addresses/me")
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "street": "1 Test Lane",
                                          "city": "Cape Town",
                                          "state": "Western Cape",
                                          "zip": "8000",
                                          "country": "South Africa"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andReturn();

        return readTree(addressResponse).path("addressId").asLong();
    }

    private String randomPassword() {
        return "pw-" + UUID.randomUUID().toString().replace("-", "");
    }

    private void addItemToCart(String email, String password, Long productId, int quantity) throws Exception {
        mockMvc.perform(
                        post("/api/carts/me/items")
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "productId": %d,
                                          "quantity": %d
                                        }
                                        """.formatted(productId, quantity))
                )
                .andExpect(status().isOk());
    }

    private JsonNode checkoutPreview(String email, String password, long addressId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/checkouts/preview")
                                .with(httpBasic(email, password))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "shippingAddressId": %d
                                        }
                                        """.formatted(addressId))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(result);
    }

    private JsonNode confirmCheckout(String email, String password, long addressId, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/checkouts/confirm")
                                .with(httpBasic(email, password))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "shippingAddressId": %d,
                                          "paymentMethod": "SIMULATED"
                                        }
                                        """.formatted(addressId))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(result);
    }

    private JsonNode getMyOrders(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/orders/me")
                                .with(httpBasic(email, password))
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(result);
    }

    private JsonNode searchCompare(String email, String password, String query) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/search/compare")
                                .with(httpBasic(email, password))
                                .param("q", query)
                                .param("limit", "10")
                )
                .andExpect(status().isOk())
                .andReturn();
        return readTree(result);
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
}
