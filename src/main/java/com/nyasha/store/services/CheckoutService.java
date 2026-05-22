package com.nyasha.store.services;

import com.nyasha.store.dtos.checkout.CheckoutConfirmRequest;
import com.nyasha.store.dtos.checkout.CheckoutConfirmResponse;
import com.nyasha.store.dtos.checkout.CheckoutLineItemResponse;
import com.nyasha.store.dtos.checkout.CheckoutPreviewRequest;
import com.nyasha.store.dtos.checkout.CheckoutPreviewResponse;
import com.nyasha.store.entities.Address;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.entities.IdempotencyKey;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.ShippingInfo;
import com.nyasha.store.entities.User;
import com.nyasha.store.enums.OrderStatus;
import com.nyasha.store.enums.ShippingStatus;
import com.nyasha.store.services.payment.PaymentService;
import com.nyasha.store.repositories.IdempotencyKeyRepository;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.PaymentRepository;
import com.nyasha.store.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class CheckoutService {

    public static final String IDEMPOTENCY_OPERATION = "checkout_confirm";
    private static final double MIN_TAX_RATE = 0.0;
    private static final double MAX_TAX_RATE = 1.0;
    private static final double MIN_SHIPPING_COST = 0.0;

    private final CartService cartService;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final AddressService addressService;

    public CheckoutService(
            CartService cartService,
            UserRepository userRepository,
            InventoryService inventoryService,
            PricingService pricingService,
            OrderRepository orderRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            AddressService addressService
    ) {
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.orderRepository = orderRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.addressService = addressService;
    }

    public CheckoutPreviewResponse preview(Long userId, CheckoutPreviewRequest request) {
        request = sanitizePreviewRequest(request);
        Cart cart = cartService.getOrCreateForUser(userId);
        validateCart(cart);
        if (request.shippingAddressId() != null) {
            addressService.validateOwnership(userId, request.shippingAddressId());
        }
        validateCheckoutPreviewRequest(request);

        PricingService.PricingResult pricing = pricingService.compute(cart, request);
        return asPreviewResponse(pricing.lines(), pricing.subtotal(), pricing.discountAmount(), pricing.shippingAmount(), pricing.taxAmount(), pricing.totalAmount());
    }

    @Transactional
    public CheckoutConfirmResponse confirm(Long userId, CheckoutConfirmRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        request = sanitizeRequest(request);
        validateCheckoutConfirmRequest(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Address shippingAddress = addressService.validateOwnership(userId, request.shippingAddressId());
        Cart cart = cartService.getOrCreateForUser(userId);
        validateCart(cart);
        validateCartItems(cart);

        String requestHash = hashRequest(request, userId, cart);

        Optional<Order> existingOrder = idempotencyKeyRepository
                .findByKeyValueAndOperation(idempotencyKey, IDEMPOTENCY_OPERATION)
                .filter(key -> Objects.equals(key.getUserId(), userId))
                .filter(key -> requestHash.equals(key.getRequestHash()))
                .flatMap(key -> {
                    Long orderId = key.getOrderId();
                    return orderId == null ? Optional.empty() : orderRepository.findById(orderId);
                });
        if (existingOrder.isPresent()) {
            return toConfirmResponse(existingOrder.get());
        }

        idempotencyKeyRepository.findByKeyValueAndOperation(idempotencyKey, IDEMPOTENCY_OPERATION)
                .ifPresent(existing -> {
                    if (!Objects.equals(existing.getUserId(), userId)) {
                        throw new RuntimeException("Idempotency key belongs to another user");
                    }
                    throw new RuntimeException("Duplicate idempotency key with different request");
                });

        PricingService.PricingResult pricing = pricingService.compute(
                cart,
                new CheckoutPreviewRequest(
                        request.couponCode(),
                        request.shippingCost(),
                        request.taxRate(),
                        request.shippingAddressId()
                )
        );

        List<ReservedStock> reservedStock = reserveInventory(cart);

        Order order = buildOrder(user, cart, pricing.totalAmount().doubleValue());
        ShippingInfo shippingInfo = buildShippingInfo(order, shippingAddress);
        order.setShippingInfo(shippingInfo);

        try {
            Order savedOrder = orderRepository.save(order);
            paymentService.createPayment(
                    savedOrder.getOrderId(),
                    request.paymentMethod() == null || request.paymentMethod().isBlank()
                            ? "SIMULATED"
                            : request.paymentMethod(),
                    pricing.totalAmount().doubleValue()
            );
            paymentService.capturePayment(savedOrder.getOrderId());

            savedOrder.setStatus(OrderStatus.PAID.name());
            cartService.clear(userId);
            createIdempotencyKey(idempotencyKey, userId, savedOrder.getOrderId(), requestHash);
            Order finalized = orderRepository.save(savedOrder);
            return toConfirmResponse(finalized, pricing);
        } catch (RuntimeException ex) {
            for (ReservedStock stock : reservedStock) {
                if (stock.quantity() > 0) {
                    inventoryService.release(stock.productId(), stock.variantId(), stock.quantity());
                }
            }
            throw ex;
        }
    }

    private ShippingInfo buildShippingInfo(Order order, Address shippingAddress) {
        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setOrder(order);
        shippingInfo.setAddressId(shippingAddress.getAddressId());
        shippingInfo.setStatus(ShippingStatus.CREATED.name());
        shippingInfo.setEstimatedDelivery(LocalDateTime.now().plusDays(5));
        return shippingInfo;
    }

    private Order buildOrder(User user, Cart cart, Double totalAmount) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setTotalAmount(totalAmount);
        order.setOrderItems(buildOrderItems(order, cart));
        return order;
    }

    private List<ReservedStock> reserveInventory(Cart cart) {
        List<ReservedStock> reserved = new ArrayList<>();
        for (var item : cart.getCartItems()) {
            Long productId = item.getProduct().getProductId();
            Long variantId = item.getVariant() == null ? null : item.getVariant().getVariantId();
            int quantity = item.getQuantity();

            inventoryService.reserve(productId, variantId, quantity);
            reserved.add(new ReservedStock(productId, variantId, quantity));
        }
        return reserved;
    }

    private void createIdempotencyKey(String idempotencyKey, Long userId, Long orderId, String requestHash) {
        IdempotencyKey key = new IdempotencyKey();
        key.setKeyValue(idempotencyKey);
        key.setOperation(IDEMPOTENCY_OPERATION);
        key.setUserId(userId);
        key.setOrderId(orderId);
        key.setRequestHash(requestHash);
        key.setCreatedAt(LocalDateTime.now());
        key.setExpiresAt(LocalDateTime.now().plusHours(24));
        idempotencyKeyRepository.save(key);
    }

    private void validateCart(Cart cart) {
        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }
    }

    private void validateCartItems(Cart cart) {
        for (var item : cart.getCartItems()) {
            if (item.getProduct() == null || item.getProduct().getProductId() == null) {
                throw new RuntimeException("Cart item is missing product");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new RuntimeException("Cart item quantity must be positive");
            }
        }
    }

    private CheckoutConfirmRequest sanitizeRequest(CheckoutConfirmRequest request) {
        return new CheckoutConfirmRequest(
                request == null ? null : request.paymentMethod(),
                request == null ? null : request.couponCode(),
                request == null ? null : request.shippingCost(),
                request == null ? null : request.taxRate(),
                request == null ? null : request.shippingAddressId()
        );
    }

    private CheckoutPreviewRequest sanitizePreviewRequest(CheckoutPreviewRequest request) {
        return new CheckoutPreviewRequest(
                request == null ? null : request.couponCode(),
                request == null ? null : request.shippingCost(),
                request == null ? null : request.taxRate(),
                request == null ? null : request.shippingAddressId()
        );
    }

    private void validateCheckoutPreviewRequest(CheckoutPreviewRequest request) {
        validateTaxRate(request.taxRate());
        validateShippingCost(request.shippingCost());
    }

    private void validateCheckoutConfirmRequest(CheckoutConfirmRequest request) {
        if (request.shippingAddressId() == null) {
            throw new IllegalArgumentException("shippingAddressId is required");
        }
        validateTaxRate(request.taxRate());
        validateShippingCost(request.shippingCost());
        if (request.paymentMethod() != null && request.paymentMethod().isBlank()) {
            throw new IllegalArgumentException("paymentMethod must be null or non-blank");
        }
    }

    private void validateTaxRate(Double taxRate) {
        if (taxRate == null) {
            return;
        }
        if (taxRate < MIN_TAX_RATE || taxRate > MAX_TAX_RATE) {
            throw new IllegalArgumentException("taxRate must be between 0 and 1");
        }
    }

    private void validateShippingCost(Double shippingCost) {
        if (shippingCost != null && shippingCost < MIN_SHIPPING_COST) {
            throw new IllegalArgumentException("shippingCost cannot be negative");
        }
    }

    private Set<OrderItem> buildOrderItems(Order order, Cart cart) {
        Set<OrderItem> orderItems = new HashSet<>();

        for (var item : cart.getCartItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(item.getProduct());
            orderItem.setVariant(item.getVariant());
            orderItem.setQuantity(item.getQuantity());

            double basePrice = item.getProduct() == null || item.getProduct().getBasePrice() == null
                    ? 0.0
                    : item.getProduct().getBasePrice();
            double adjustment = item.getVariant() == null || item.getVariant().getPriceAdjustment() == null
                    ? 0.0
                    : item.getVariant().getPriceAdjustment();
            orderItem.setPriceAtPurchase(basePrice + adjustment);
            orderItems.add(orderItem);
        }

        return orderItems;
    }

    private CheckoutPreviewResponse asPreviewResponse(List<CheckoutLineItemResponse> lines, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingAmount, BigDecimal taxAmount, BigDecimal totalAmount) {
        return new CheckoutPreviewResponse(
                subtotal.doubleValue(),
                discountAmount.doubleValue(),
                shippingAmount.doubleValue(),
                taxAmount.doubleValue(),
                totalAmount.doubleValue(),
                lines
        );
    }

    private CheckoutConfirmResponse toConfirmResponse(Order order, PricingService.PricingResult pricing) {
        Payment payment = paymentRepository.findByOrderOrderId(order.getOrderId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        return new CheckoutConfirmResponse(
                order.getOrderId(),
                order.getStatus(),
                order.getTotalAmount(),
                pricing == null ? 0.0 : pricing.discountAmount().doubleValue(),
                pricing == null ? 0.0 : pricing.shippingAmount().doubleValue(),
                pricing == null ? 0.0 : pricing.taxAmount().doubleValue(),
                order.getTotalAmount(),
                payment.getStatus(),
                order.getOrderItems().stream()
                .map(item -> {
                    Double unitPrice = item.getPriceAtPurchase();
                    Integer quantity = item.getQuantity();
                    return new CheckoutLineItemResponse(
                            item.getProduct() == null ? null : item.getProduct().getProductId(),
                            item.getVariant() == null ? null : item.getVariant().getVariantId(),
                            item.getProduct() == null ? null : item.getProduct().getName(),
                            quantity,
                            unitPrice,
                            toLineTotal(unitPrice, quantity)
                    );
                })
                        .toList()
        );
    }

    private CheckoutConfirmResponse toConfirmResponse(Order order) {
        return new CheckoutConfirmResponse(
                order.getOrderId(),
                order.getStatus(),
                order.getTotalAmount(),
                0.0,
                0.0,
                0.0,
                order.getTotalAmount(),
                paymentRepository.findByOrderOrderId(order.getOrderId())
                        .map(Payment::getStatus)
                        .orElse("FAILED"),
                order.getOrderItems().stream()
                .map(item -> {
                    Double unitPrice = item.getPriceAtPurchase();
                    Integer quantity = item.getQuantity();
                    return new CheckoutLineItemResponse(
                            item.getProduct() == null ? null : item.getProduct().getProductId(),
                            item.getVariant() == null ? null : item.getVariant().getVariantId(),
                            item.getProduct() == null ? null : item.getProduct().getName(),
                            quantity,
                            unitPrice,
                            toLineTotal(unitPrice, quantity)
                    );
                })
                        .toList()
        );
    }

    private double toLineTotal(Double unitPrice, Integer quantity) {
        if (unitPrice == null || quantity == null) {
            return 0.0;
        }
        return unitPrice * quantity;
    }

    private String hashRequest(CheckoutConfirmRequest request, Long userId, Cart cart) {
        StringBuilder builder = new StringBuilder();
        builder.append(userId)
                .append('|')
                .append(request.paymentMethod())
                .append('|')
                .append(request.couponCode())
                .append('|')
                .append(request.shippingCost())
                .append('|')
                .append(request.taxRate())
                .append('|')
                .append(request.shippingAddressId());

        cart.getCartItems().stream()
                .map(item -> {
                    Long productId = item.getProduct() == null ? null : item.getProduct().getProductId();
                    Long variantId = item.getVariant() == null ? null : item.getVariant().getVariantId();
                    return productId + ":" + variantId + ":" + item.getQuantity();
                })
                .sorted()
                .forEach(entry -> builder.append('|').append(entry));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Checksum generation failed", e);
        }
    }

    private record ReservedStock(Long productId, Long variantId, int quantity) {}
}
