package com.nyasha.store.configurations;

import com.nyasha.store.configurations.SecurityConfig;
import com.nyasha.store.controllers.CartController;
import com.nyasha.store.controllers.InventoryController;
import com.nyasha.store.controllers.OrderController;
import com.nyasha.store.controllers.PaymentController;
import com.nyasha.store.controllers.ReturnsController;
import com.nyasha.store.controllers.ReviewController;
import com.nyasha.store.controllers.SupplierController;
import com.nyasha.store.controllers.WishlistController;
import com.nyasha.store.entities.Inventory;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.Return;
import com.nyasha.store.entities.Review;
import com.nyasha.store.entities.Supplier;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.CartService;
import com.nyasha.store.services.InventoryService;
import com.nyasha.store.services.OrderService;
import com.nyasha.store.services.ReturnService;
import com.nyasha.store.services.ReviewService;
import com.nyasha.store.services.SupplierService;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.WishlistService;
import com.nyasha.store.services.payment.PaymentService;
import com.nyasha.store.security.DatabaseUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CartController.class,
        OrderController.class,
        ReturnsController.class,
        WishlistController.class,
        ReviewController.class,
        InventoryController.class,
        SupplierController.class,
        PaymentController.class
}, properties = {"spring.security.user.name=test-user","spring.security.user.password=test-password"})
@Import(SecurityConfig.class)
class SecurityConfigTest {

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
    private DatabaseUserDetailsService databaseUserDetailsService;

    @BeforeEach
    void setUp() {
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(testUser(1L, "user@example.com")));
        when(userService.getUserByEmail("admin@example.com")).thenReturn(Optional.of(testUser(2L, "admin@example.com")));
    }

    @Test
    void cartsAndWishlistsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/carts/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wishlists/me").with(user("user@example.com").roles("USER"))).andExpect(status().isOk());
    }

    @Test
    void orderAdminTransitionRequiresAdminRole() throws Exception {
        when(orderService.packOrder(1L)).thenReturn(new Order());

        mockMvc.perform(post("/api/orders/1/pack").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/1/pack").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void returnDecisionEndpointsRequireAdminForApproveRejectAndUserForRefund() throws Exception {
        Return approved = new Return();
        when(returnService.approveReturn(1L)).thenReturn(approved);
        when(returnService.refundReturn(1L, 1L, "refund-key")).thenReturn(approved);

        mockMvc.perform(post("/api/returns/1/approve").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/returns/1/approve").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/returns/1/refund")
                                .with(user("user@example.com").roles("USER"))
                                .header("Idempotency-Key", "refund-key")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());
    }

    @Test
    void reviewsRequireAuthenticatedUser() throws Exception {
        Review review = new Review();
        when(reviewService.createReview(1L, new com.nyasha.store.dtos.review.CreateReviewRequest(11L, 5, "good")))
                .thenReturn(review);

        mockMvc.perform(
                        post("/api/reviews")
                                .with(user("user@example.com").roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"productId":11,"rating":5,"comment":"good"}
                                        """)
                )
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reviews")).andExpect(status().isUnauthorized());
    }

    @Test
    void inventoryLowStockEndpointRequiresAdmin() throws Exception {
        Inventory inventory = new Inventory();
        when(inventoryService.lowStock()).thenReturn(java.util.List.of(inventory));

        mockMvc.perform(get("/api/inventory/low-stock").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/inventory/low-stock").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void supplierWriteEndpointRequiresAdmin() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setName("Acme");
        when(supplierService.createSupplier(org.mockito.ArgumentMatchers.any(Supplier.class)))
                .thenReturn(supplier);

        mockMvc.perform(
                        post("/api/suppliers")
                                .with(user("user@example.com").roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Acme"}
                                        """)
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/suppliers")
                                .with(user("admin@example.com").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Acme"}
                                        """)
                )
                .andExpect(status().isCreated());
    }

    @Test
    void paymentCaptureRequiresAdminRole() throws Exception {
        Payment payment = new Payment();
        when(paymentService.capturePayment(1L)).thenReturn(payment);

        mockMvc.perform(post("/api/payments/orders/1/capture").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/payments/orders/1/capture").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    private User testUser(Long id, String email) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        return user;
    }
}
