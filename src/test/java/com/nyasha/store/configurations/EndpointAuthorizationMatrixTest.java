package com.nyasha.store.configurations;

import com.nyasha.store.benchmark.controllers.BenchmarkController;
import com.nyasha.store.benchmark.dtos.BenchmarkRunSummaryDto;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.services.BenchmarkService;
import com.nyasha.store.controllers.AddressController;
import com.nyasha.store.controllers.CartController;
import com.nyasha.store.controllers.CategoryController;
import com.nyasha.store.controllers.CheckoutController;
import com.nyasha.store.controllers.IndexController;
import com.nyasha.store.controllers.InventoryController;
import com.nyasha.store.controllers.OrderController;
import com.nyasha.store.controllers.PaymentController;
import com.nyasha.store.controllers.ProductController;
import com.nyasha.store.controllers.ReturnsController;
import com.nyasha.store.controllers.ReviewController;
import com.nyasha.store.controllers.SearchController;
import com.nyasha.store.controllers.SupplierController;
import com.nyasha.store.controllers.UserController;
import com.nyasha.store.controllers.WishlistController;
import com.nyasha.store.entities.Address;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.Return;
import com.nyasha.store.entities.Review;
import com.nyasha.store.entities.Supplier;
import com.nyasha.store.entities.User;
import com.nyasha.store.entities.Wishlist;
import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import com.nyasha.store.observability.OperationsController;
import com.nyasha.store.observability.OperationalStatusService;
import com.nyasha.store.observability.OperationalStatusSnapshot;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.search.SearchCompareResponse;
import com.nyasha.store.search.SearchEngineType;
import com.nyasha.store.search.SearchResult;
import com.nyasha.store.services.AddressService;
import com.nyasha.store.services.CartService;
import com.nyasha.store.services.CategoryService;
import com.nyasha.store.services.CheckoutService;
import com.nyasha.store.security.DatabaseUserDetailsService;
import com.nyasha.store.services.InventoryService;
import com.nyasha.store.services.OrderService;
import com.nyasha.store.services.ProductService;
import com.nyasha.store.services.ReturnService;
import com.nyasha.store.services.ReviewService;
import com.nyasha.store.services.SupplierService;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.WishlistService;
import com.nyasha.store.services.payment.PaymentService;
import com.nyasha.store.indexing.SearchIndexSyncService;
import com.nyasha.store.dtos.review.CreateReviewRequest;
import com.nyasha.store.dtos.returns.CreateReturnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CartController.class,
        CheckoutController.class,
        OrderController.class,
        ReturnsController.class,
        WishlistController.class,
        ReviewController.class,
        InventoryController.class,
        SupplierController.class,
        PaymentController.class,
        AddressController.class,
        UserController.class,
        SearchController.class,
        ProductController.class,
        CategoryController.class,
        BenchmarkController.class,
        OperationsController.class,
        IndexController.class
}, properties = {"spring.security.user.name=test-user", "spring.security.user.password=test-password"})
@Import(SecurityConfig.class)
class EndpointAuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private ReturnService returnService;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private SupplierService supplierService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private UserService userService;

    @MockBean
    private AddressService addressService;

    @MockBean
    private CheckoutService checkoutService;

    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private ProductSearchService productSearchService;

    @MockBean
    private SearchIndexSyncService searchIndexSyncService;

    @MockBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @MockBean
    private BenchmarkService benchmarkService;

    @MockBean
    private IndexManagementService indexManagementService;

    @MockBean
    private OperationalStatusService operationalStatusService;

    private static final String USER_EMAIL = "user@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @BeforeEach
    void setUp() throws IOException {
        User user = testUser(1L, USER_EMAIL);
        User admin = testUser(2L, ADMIN_EMAIL);
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
        when(userService.getUserByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userService.authenticateUser(anyString(), anyString()))
                .thenReturn(Optional.of(user));

        when(cartService.getForUser(anyLong())).thenReturn(new Cart());
        when(cartService.addItem(anyLong(), any())).thenReturn(new Cart());
        when(cartService.updateItemQuantity(anyLong(), anyLong(), anyInt())).thenReturn(new Cart());
        doNothing().when(cartService).removeItem(anyLong(), anyLong());
        doNothing().when(cartService).clear(anyLong());

        when(orderService.getOrderSummaries(anyLong())).thenReturn(List.of(sampleOrderSummary()));
        when(orderService.getMyOrderSummary(anyLong(), anyLong())).thenReturn(sampleOrderSummary());
        when(orderService.packOrder(anyLong())).thenReturn(sampleOrder());
        when(orderService.shipOrder(anyLong(), any(), any())).thenReturn(sampleOrder());
        when(orderService.deliverOrder(anyLong())).thenReturn(sampleOrder());
        when(orderService.cancelOrder(anyLong(), anyLong())).thenReturn(sampleOrder());
        when(orderService.getMyOrder(anyLong(), anyLong())).thenReturn(sampleOrder());

        when(paymentService.getPayment(anyLong())).thenReturn(samplePayment());
        when(paymentService.capturePayment(anyLong())).thenReturn(samplePayment());
        when(paymentService.refundPayment(anyLong(), anyDouble())).thenReturn(samplePayment());

        when(inventoryService.lowStock()).thenReturn(List.of(new Inventory()));
        when(inventoryService.getInventory(anyLong(), anyLong())).thenReturn(new Inventory());
        when(inventoryService.upsert(anyLong(), anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn(new Inventory());
        when(inventoryService.adjust(anyLong(), anyLong(), anyInt())).thenReturn(new Inventory());

        when(supplierService.getSuppliers()).thenReturn(List.of(new Supplier()));
        when(supplierService.getSupplier(anyLong())).thenReturn(new Supplier());
        when(supplierService.createSupplier(any())).thenReturn(new Supplier());
        when(supplierService.updateSupplier(anyLong(), any())).thenReturn(new Supplier());
        doNothing().when(supplierService).deleteSupplier(anyLong());

        when(returnService.openReturn(anyLong(), anyLong(), any(CreateReturnRequest.class))).thenReturn(new Return());
        when(returnService.getReturnsForUser(anyLong())).thenReturn(List.of(new Return()));
        when(returnService.approveReturn(anyLong())).thenReturn(new Return());
        when(returnService.rejectReturn(anyLong())).thenReturn(new Return());
        when(returnService.refundReturn(anyLong(), anyLong(), anyString())).thenReturn(new Return());

        when(reviewService.createReview(anyLong(), any(CreateReviewRequest.class))).thenReturn(new Review());
        when(reviewService.listForProduct(anyLong())).thenReturn(List.of(new Review()));

        when(wishlistService.getOrCreate(anyLong())).thenReturn(new Wishlist());
        when(wishlistService.addItem(anyLong(), any())).thenReturn(new Wishlist());
        doNothing().when(wishlistService).removeItem(anyLong(), anyLong());

        when(addressService.createAddress(anyLong(), any(Address.class))).thenReturn(new Address());
        when(addressService.getAddressByIdForUser(anyLong(), anyLong())).thenReturn(Optional.of(new Address()));
        when(addressService.getAllAddressesForUser(anyLong())).thenReturn(List.of(new Address()));
        when(addressService.updateAddress(anyLong(), anyLong(), any(Address.class))).thenReturn(new Address());
        doNothing().when(addressService).deleteAddress(anyLong(), anyLong());

        when(userService.getUserById(anyLong())).thenReturn(Optional.of(testUser(3L, "lookup@example.com")));
        when(userService.getAllUsers()).thenReturn(List.of(testUser(3L, "lookup@example.com")));
        when(userService.searchUsers(anyString())).thenReturn(List.of(testUser(4L, "lookup@example.com")));
        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(new User());
        doNothing().when(userService).deleteUser(anyLong());
        when(userService.createUser(any(User.class))).thenReturn(new User());

        Product sampleProduct = sampleProduct();
        when(productService.getAllProducts()).thenReturn(List.of(sampleProduct));
        when(productService.getProductById(anyLong())).thenReturn(sampleProduct);
        when(productService.createProduct(any(Product.class))).thenReturn(sampleProduct);
        when(productService.updateProduct(anyLong(), any(Product.class))).thenReturn(sampleProduct);
        doNothing().when(productService).deleteProduct(anyLong());
        when(productService.autocomplete(anyString())).thenReturn(List.of(sampleProduct));
        when(productService.searchByText(anyString())).thenReturn(new java.util.HashSet<>(List.of(sampleProduct)));
        when(productService.getProductsByCategory(anyString())).thenReturn(List.of(sampleProduct));

        Category category = new Category();
        category.setName("Category");
        when(categoryService.getAllCategories()).thenReturn(List.of(category));
        when(categoryService.getCategoryById(anyLong())).thenReturn(Optional.of(category));
        when(categoryService.createCategory(any(Category.class))).thenReturn(category);
        when(categoryService.updateCategory(anyLong(), any(Category.class))).thenReturn(category);
        doNothing().when(categoryService).deleteCategory(anyLong());
        when(categoryService.searchByName(anyString())).thenReturn(List.of(category));

        SearchResult sampleSearchResult = SearchResult.success(SearchEngineType.IN_MEMORY, 7L, List.of(sampleProduct));
        when(productSearchService.search(anyString(), anyString(), anyInt())).thenReturn(sampleSearchResult);
        when(productSearchService.compare(anyString(), anyInt()))
                .thenReturn(new SearchCompareResponse("query", 20, List.of(sampleSearchResult)));
        when(searchIndexSyncService.suggest(anyString(), anyInt())).thenReturn(List.of("suggestion"));

        when(checkoutService.preview(anyLong(), any())).thenReturn(null);
        when(checkoutService.confirm(anyLong(), any(), anyString())).thenReturn(null);

        Path reportRoot = Files.createTempDirectory("benchmark-reports");
        when(benchmarkService.startRun(anyLong(), anyInt())).thenReturn(new com.nyasha.store.benchmark.dtos.BenchmarkRunResponse(1L, "QUEUED", 1));
        when(benchmarkService.getRun(anyLong())).thenReturn(sampleRunSummary());
        when(benchmarkService.getResults(anyLong())).thenReturn(List.of());
        when(benchmarkService.buildReportMarkdown(anyLong())).thenReturn("summary");
        when(benchmarkService.buildReportJson(anyLong())).thenReturn("{\"status\":\"ok\"}");
        when(benchmarkService.buildLatencyCsv(anyLong())).thenReturn("header\n100");
        when(benchmarkService.buildRelevanceCsv(anyLong())).thenReturn("header\n1");
        when(benchmarkService.getReportPath(anyLong(), anyString())).thenAnswer(invocation -> {
            String filename = invocation.getArgument(1, String.class);
            return reportRoot.resolve(filename);
        });

        Files.writeString(reportRoot.resolve("summary.md"), "summary");
        Files.writeString(reportRoot.resolve("results.json"), "{\"status\":\"ok\"}");
        Files.writeString(reportRoot.resolve("latency.csv"), "latency");
        Files.writeString(reportRoot.resolve("relevance.csv"), "relevance");

        when(indexManagementService.getStatus()).thenReturn(new IndexingStatusSnapshot(
                12L,
                0L,
                0L,
                0L,
                0L,
                false,
                "never"
        ));
        doNothing().when(indexManagementService).rebuildIndex();

        when(operationalStatusService.getStatus()).thenReturn(new OperationalStatusSnapshot(
                "UP",
                LocalDateTime.now().toString(),
                "green",
                true,
                12L,
                0L,
                0L,
                0L,
                false,
                "never",
                0L,
                0L,
                0L,
                List.of()
        ));
    }

    @Test
    void endpointMatrixMatchesExpectedSecurityOutcome() throws Exception {
        for (AuthorizationCase authorizationCase : endpointMatrix()) {
            assertUnauthorized(authorizationCase);
            assertUser(authorizationCase);
            assertAdmin(authorizationCase);
        }
    }

    private static List<AuthorizationCase> endpointMatrix() {
        String productBody = """
                {"name":"Demo","sku":"D-1","basePrice":9.99}
                """;
        String categoryBody = """
                {"name":"Default"}
                """;
        String supplierBody = """
                {"name":"Acme"}
                """;

        return List.of(
                new AuthorizationCase("GET", "/api/products", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/products/1", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/products/search?query=laptop", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/products/autocomplete?prefix=lap", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/products/category/1", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/categories", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/categories/1", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/search?q=laptop", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/search/compare?q=laptop", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/search/autocomplete?q=lap", null, null, 200, 200, 200),
                new AuthorizationCase("GET", "/api/reviews/products/1", null, null, 200, 200, 200),
                new AuthorizationCase("POST", "/api/reviews", """
                        {"productId":1,"rating":5,"comment":"good"}
                        """, null, 401, 200, 200),

                new AuthorizationCase("GET", "/api/carts/me", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/carts/me/items", """
                        {"productId":1,"quantity":1}
                        """, null, 401, 200, 200),
                new AuthorizationCase("PATCH", "/api/carts/me/items/1", """
                        {"quantity":1}
                        """, null, 401, 200, 200),
                new AuthorizationCase("DELETE", "/api/carts/me/items/1", null, null, 401, 204, 204),
                new AuthorizationCase("DELETE", "/api/carts/me", null, null, 401, 204, 204),

                new AuthorizationCase("POST", "/api/checkouts/preview", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/checkouts/confirm", """
                        {"shippingAddressId":1}
                        """, "Idempotency-Key", 401, 200, 200),

                new AuthorizationCase("GET", "/api/orders/me", null, null, 401, 200, 200),
                new AuthorizationCase("GET", "/api/orders/1", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/orders/1/pack", null, null, 401, 403, 200),
                new AuthorizationCase("POST", "/api/orders/1/ship", null, null, 401, 403, 200),
                new AuthorizationCase("POST", "/api/orders/1/delivered", null, null, 401, 403, 200),
                new AuthorizationCase("POST", "/api/orders/1/cancel", null, null, 401, 200, 200),

                new AuthorizationCase("GET", "/api/payments/orders/1", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/payments/orders/1/capture", null, null, 401, 403, 200),
                new AuthorizationCase("POST", "/api/payments/orders/1/refund", "{\"amount\":10.0}", null, 401, 403, 200),

                new AuthorizationCase("GET", "/api/inventory/low-stock", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/inventory/1", null, null, 401, 200, 200),
                new AuthorizationCase("PUT", "/api/inventory/1", """
                        {"quantity":10,"location":"dc-1","reorderThreshold":5}
                        """, null, 401, 403, 200),
                new AuthorizationCase("PATCH", "/api/inventory/1/adjust", """
                        {"delta":1}
                        """, null, 401, 403, 200),

                new AuthorizationCase("GET", "/api/suppliers", null, null, 401, 200, 200),
                new AuthorizationCase("GET", "/api/suppliers/1", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/suppliers", supplierBody, null, 401, 403, 201),
                new AuthorizationCase("PUT", "/api/suppliers/1", supplierBody, null, 401, 403, 200),
                new AuthorizationCase("DELETE", "/api/suppliers/1", null, null, 401, 403, 204),

                new AuthorizationCase("POST", "/api/returns/1", """
                        {"orderItemId":1,"reason":"defect"}
                        """, null, 401, 200, 200),
                new AuthorizationCase("GET", "/api/returns/me", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/returns/1/approve", null, null, 401, 403, 200),
                new AuthorizationCase("POST", "/api/returns/1/reject", null, null, 401, 403, 200),
                new AuthorizationCase("POST", "/api/returns/1/refund", null, "Idempotency-Key", 401, 200, 200),

                new AuthorizationCase("GET", "/api/wishlists/me", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/wishlists/me/items", """
                        {"productId":1}
                        """, null, 401, 200, 200),
                new AuthorizationCase("DELETE", "/api/wishlists/me/items/1", null, null, 401, 204, 204),

                new AuthorizationCase("GET", "/api/addresses/me", null, null, 401, 200, 200),
                new AuthorizationCase("GET", "/api/addresses/me/1", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/api/addresses/me", """
                        {"street":"1 Commerce","city":"Metro","country":"Country"}
                        """, null, 401, 200, 200),
                new AuthorizationCase("PUT", "/api/addresses/me/1", """
                        {"street":"1 Commerce","city":"Metro","country":"Country"}
                        """, null, 401, 200, 200),
                new AuthorizationCase("DELETE", "/api/addresses/me/1", null, null, 401, 204, 204),

                new AuthorizationCase("GET", "/users", null, null, 401, 200, 200),
                new AuthorizationCase("GET", "/users/search?query=lookup", null, null, 401, 200, 200),
                new AuthorizationCase("GET", "/users/1", null, null, 401, 200, 200),
                new AuthorizationCase("POST", "/users/register", """
                        {"name":"User","email":"register@example.com","hashedPassword":"secret"}
                        """, null, 201, 201, 201),
                new AuthorizationCase("POST", "/users/login", """
                        {"email":"user@example.com","password":"secret"}
                        """, null, 200, 200, 200),
                new AuthorizationCase("PUT", "/users/1", """
                        {"name":"User","email":"user@example.com","hashedPassword":"secret"}
                        """, null, 401, 403, 200),
                new AuthorizationCase("DELETE", "/users/1", null, null, 401, 403, 204),

                new AuthorizationCase("POST", "/api/products", productBody, null, 401, 403, 201),
                new AuthorizationCase("PUT", "/api/products/1", productBody, null, 401, 403, 200),
                new AuthorizationCase("DELETE", "/api/products/1", null, null, 401, 403, 204),
                new AuthorizationCase("POST", "/api/categories", categoryBody, null, 401, 403, 200),
                new AuthorizationCase("PUT", "/api/categories/1", categoryBody, null, 401, 403, 200),
                new AuthorizationCase("DELETE", "/api/categories/1", null, null, 401, 403, 204),

                new AuthorizationCase("POST", "/api/benchmarks/runs", null, null, 401, 403, 202),
                new AuthorizationCase("GET", "/api/benchmarks/runs", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/results", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/artifacts/summary.md", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/artifacts/results.json", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/artifacts/latency.csv", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/artifacts/relevance.csv", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/report.md", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/report.json", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/latency.csv", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/relevance.csv", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/benchmarks/runs/1/artifacts/missing.csv", null, null, 401, 403, 400),
                new AuthorizationCase("POST", "/api/index/rebuild", null, null, 401, 403, 202),
                new AuthorizationCase("GET", "/api/index/status", null, null, 401, 403, 200),
                new AuthorizationCase("GET", "/api/ops/status", null, null, 401, 403, 200)
        );
    }

    private void assertUnauthorized(AuthorizationCase authorizationCase) throws Exception {
        mockMvc.perform(requestFor(authorizationCase))
                .andExpect(status().is(authorizationCase.unauthorizedStatus()));
    }

    private void assertUser(AuthorizationCase authorizationCase) throws Exception {
        mockMvc.perform(requestFor(authorizationCase).with(user(USER_EMAIL).roles("USER")))
                .andExpect(status().is(authorizationCase.userStatus()));
    }

    private void assertAdmin(AuthorizationCase authorizationCase) throws Exception {
        mockMvc.perform(requestFor(authorizationCase).with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().is(authorizationCase.adminStatus()));
    }

    private MockHttpServletRequestBuilder requestFor(AuthorizationCase authorizationCase) {
        MockHttpServletRequestBuilder request = switch (authorizationCase.method()) {
            case "GET" -> get(authorizationCase.path());
            case "POST" -> post(authorizationCase.path());
            case "PUT" -> put(authorizationCase.path());
            case "PATCH" -> patch(authorizationCase.path());
            case "DELETE" -> delete(authorizationCase.path());
            default -> throw new IllegalArgumentException("Unsupported method: " + authorizationCase.method());
        };

        if (authorizationCase.body() != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(authorizationCase.body());
        }
        if (authorizationCase.idempotencyHeaderKey() != null) {
            request = request.header("Idempotency-Key", "idempotency-key");
        }
        return request;
    }

    private static User testUser(Long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setName("Fixture User");
        return user;
    }

    private static Order sampleOrder() {
        return new Order();
    }

    private static com.nyasha.store.dtos.order.OrderSummaryDto sampleOrderSummary() {
        return new com.nyasha.store.dtos.order.OrderSummaryDto(
                1L,
                "PENDING_PAYMENT",
                LocalDateTime.now(),
                12.34,
                "PENDING",
                "CREATED",
                List.of()
        );
    }

    private static Payment samplePayment() {
        Payment payment = new Payment();
        payment.setPaymentId(1L);
        payment.setAmount(12.34);
        return payment;
    }

    private static Product sampleProduct() {
        Product product = new Product();
        product.setProductId(1L);
        product.setName("Demo Product");
        product.setSku("SKU-1");
        product.setBasePrice(9.99);
        return product;
    }

    private static BenchmarkRunSummaryDto sampleRunSummary() {
        return new BenchmarkRunSummaryDto(
                1L,
                BenchmarkRunStatus.QUEUED,
                "electronics-basic",
                LocalDateTime.now(),
                null,
                1,
                1,
                "/api/benchmarks/runs/1/report.md",
                "/api/benchmarks/runs/1/report.json",
                "/api/benchmarks/runs/1/latency.csv",
                "/api/benchmarks/runs/1/relevance.csv",
                "/tmp",
                0L,
                0.0,
                0L,
                0L,
                0L,
                0L,
                0.0,
                0L,
                0L,
                0L,
                0.0
        );
    }

    private record AuthorizationCase(
            String method,
            String path,
            String body,
            String idempotencyHeaderKey,
            int unauthorizedStatus,
            int userStatus,
            int adminStatus
    ) {
    }
}
