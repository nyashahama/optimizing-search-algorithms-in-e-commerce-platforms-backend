package com.nyasha.store.services;

import com.nyasha.store.dtos.checkout.CheckoutConfirmRequest;
import com.nyasha.store.dtos.checkout.CheckoutPreviewRequest;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.entities.CartItem;
import com.nyasha.store.entities.Address;
import com.nyasha.store.entities.IdempotencyKey;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.User;
import com.nyasha.store.enums.OrderStatus;
import com.nyasha.store.enums.PaymentStatus;
import com.nyasha.store.repositories.IdempotencyKeyRepository;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.PaymentRepository;
import com.nyasha.store.repositories.UserRepository;
import com.nyasha.store.services.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CartService cartService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PricingService pricingService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private AddressService addressService;

    @InjectMocks
    private CheckoutService checkoutService;

    private static final Long USER_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "confirm-key-1";

    private User user;
    private Address shippingAddress;
    private Cart cart;
    private com.nyasha.store.services.PricingService.PricingResult pricing;
    private Order order;

    @BeforeEach
    void setUp() {
        user = user(1L, "buyer@example.com");
        shippingAddress = address(10L);
        cart = cart(99L, user, 101L, "Product", 100.0, 2);
        pricing = new com.nyasha.store.services.PricingService.PricingResult(
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(0.0),
                BigDecimal.valueOf(12.0),
                BigDecimal.valueOf(16.96),
                BigDecimal.valueOf(228.96),
                List.of()
        );
        order = buildOrder(501L, OrderStatus.PAID, 228.96);
    }

    @Test
    void previewReturnsComputedTotals() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(null, null, null, 10L);
        when(cartService.getOrCreateForUser(USER_ID)).thenReturn(cart);
        when(addressService.validateOwnership(USER_ID, 10L)).thenReturn(shippingAddress);
        when(pricingService.compute(cart, request)).thenReturn(pricing);

        var response = checkoutService.preview(USER_ID, request);

        assertThat(response.subtotal()).isEqualTo(200.0);
        assertThat(response.totalAmount()).isEqualTo(228.96);
    }

    @Test
    void confirmRejectsMissingIdempotencyKey() {
        assertThatThrownBy(() -> checkoutService.confirm(USER_ID, new CheckoutConfirmRequest(null, null, null, null, 10L), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key header is required");
    }

    @Test
    void confirmRejectsMissingShippingAddress() {
        assertThatThrownBy(() -> checkoutService.confirm(USER_ID, new CheckoutConfirmRequest(null, null, null, null, null), IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shippingAddressId is required");
    }

    @Test
    void confirmRejectsInvalidTaxRate() {
        assertThatThrownBy(() -> checkoutService.confirm(USER_ID, new CheckoutConfirmRequest(null, null, null, 2.0, 10L), IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taxRate must be between 0 and 1");
    }

    @Test
    void confirmCreatesOrderAndCapturesPayment() {
        order = buildOrder(null, OrderStatus.PENDING_PAYMENT, 228.96);
        when(addressService.validateOwnership(USER_ID, 10L)).thenReturn(shippingAddress);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(cartService.getOrCreateForUser(USER_ID)).thenReturn(cart);
        when(idempotencyKeyRepository.findByKeyValueAndOperation(IDEMPOTENCY_KEY, CheckoutService.IDEMPOTENCY_OPERATION))
                .thenReturn(Optional.empty());
        when(pricingService.compute(any(), any())).thenReturn(pricing);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setOrderId(saved.getOrderId() == null ? 501L : saved.getOrderId());
            return saved;
        });
        when(paymentRepository.findByOrderOrderId(501L)).thenReturn(Optional.of(payment(order)));
        when(paymentService.createPayment(501L, "SIMULATED", 228.96)).thenReturn(payment(order));
        when(paymentService.capturePayment(501L)).thenReturn(payment(order));

        var response = checkoutService.confirm(USER_ID, new CheckoutConfirmRequest(null, null, null, null, 10L), IDEMPOTENCY_KEY);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAID.name());
        assertThat(response.orderId()).isEqualTo(501L);
        verify(inventoryService).reserve(101L, null, 2);
        verify(paymentService).createPayment(501L, "SIMULATED", 228.96);
        verify(paymentService).capturePayment(501L);
    }

    @Test
    void confirmReturnsExistingOrderForRepeatedRequest() {
        when(addressService.validateOwnership(USER_ID, 10L)).thenReturn(shippingAddress);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(cartService.getOrCreateForUser(USER_ID)).thenReturn(cart);
        when(pricingService.compute(any(), any())).thenReturn(pricing);
        AtomicReference<IdempotencyKey> capturedKey = new AtomicReference<>();
        AtomicReference<IdempotencyKey> existingStoredKey = new AtomicReference<>();

        when(idempotencyKeyRepository.findByKeyValueAndOperation(IDEMPOTENCY_KEY, CheckoutService.IDEMPOTENCY_OPERATION))
                .thenAnswer(invocation -> Optional.ofNullable(existingStoredKey.get()));

        when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getOrderId() == null) {
                saved.setOrderId(501L);
            }
            return saved;
        });
        when(idempotencyKeyRepository.save(any())).thenAnswer(invocation -> {
            IdempotencyKey key = invocation.getArgument(0);
            capturedKey.set(key);
            existingStoredKey.set(key);
            return key;
        });
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderOrderId(501L)).thenReturn(Optional.of(payment(order)));
        when(paymentService.createPayment(501L, "SIMULATED", 228.96)).thenReturn(payment(order));
        when(paymentService.capturePayment(501L)).thenReturn(payment(order));

        var first = checkoutService.confirm(USER_ID, new CheckoutConfirmRequest(null, null, null, null, 10L), IDEMPOTENCY_KEY);
        assertThat(first.orderId()).isEqualTo(501L);
        assertThat(capturedKey.get()).isNotNull();

        var second = checkoutService.confirm(USER_ID, new CheckoutConfirmRequest(null, null, null, null, 10L), IDEMPOTENCY_KEY);
        assertThat(second.orderId()).isEqualTo(501L);

        verify(inventoryService, times(1)).reserve(101L, null, 2);
        verify(paymentService).createPayment(eq(501L), eq("SIMULATED"), eq(228.96));
        verify(paymentService).capturePayment(501L);
        verify(cartService).clear(USER_ID);
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        return user;
    }

    private Cart cart(Long cartId, User user, Long productId, String productName, Double unitPrice, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product(productId, productName, unitPrice));
        item.setQuantity(quantity);

        Cart cart = new Cart();
        cart.setCartId(cartId);
        cart.setUser(user);
        cart.setCartItems(new HashSet<>(Set.of(item)));
        item.setCart(cart);

        return cart;
    }

    private Order buildOrder(Long id, OrderStatus status, Double totalAmount) {
        Order order = new Order();
        order.setOrderId(id);
        order.setStatus(status.name());
        order.setTotalAmount(totalAmount);
        order.setOrderItems(new HashSet<>());
        order.getOrderItems().add(orderItem(order, product(101L, "Product", 100.0), 2));
        return order;
    }

    private OrderItem orderItem(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPriceAtPurchase(product.getBasePrice());
        return item;
    }

    private Product product(Long productId, String name, Double price) {
        Product product = new Product();
        product.setProductId(productId);
        product.setName(name);
        product.setBasePrice(price);
        return product;
    }

    private Address address(Long addressId) {
        Address address = new Address();
        address.setAddressId(addressId);
        return address;
    }

    private Payment payment(Order order) {
        Payment payment = new Payment();
        payment.setPaymentId(1L);
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.CAPTURED.name());
        payment.setAmount(order.getTotalAmount());
        payment.setMethod("SIMULATED");
        payment.setTransactionId("tx-1");
        return payment;
    }
}
