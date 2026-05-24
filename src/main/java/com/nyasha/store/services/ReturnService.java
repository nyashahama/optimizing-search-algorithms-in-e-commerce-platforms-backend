package com.nyasha.store.services;

import com.nyasha.store.dtos.returns.CreateReturnRequest;
import com.nyasha.store.entities.IdempotencyKey;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Return;
import com.nyasha.store.enums.OrderStatus;
import com.nyasha.store.enums.ReturnStatus;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.IdempotencyKeyRepository;
import com.nyasha.store.repositories.OrderItemRepository;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.ReturnRepository;
import com.nyasha.store.services.payment.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReturnService {
    public static final String IDEMPOTENCY_OPERATION = "return_refund";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRepository returnRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentService paymentService;

    public ReturnService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ReturnRepository returnRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            InventoryRepository inventoryRepository,
            PaymentService paymentService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.returnRepository = returnRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.inventoryRepository = inventoryRepository;
        this.paymentService = paymentService;
    }

    public Return openReturn(Long userId, Long orderId, CreateReturnRequest request) {
        if (request == null || request.orderItemId() == null) {
            throw new IllegalArgumentException("orderItemId is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getUser() == null || !order.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Order not found");
        }

        OrderStatus orderStatus = parseOrderStatus(order.getStatus());
        if (!isReturnable(orderStatus)) {
            throw new RuntimeException("Order is not return eligible");
        }

        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        if (!orderItem.getOrder().getOrderId().equals(orderId)) {
            throw new RuntimeException("Order item does not belong to this order");
        }

        returnRepository.findByOrderItem(orderItem).ifPresent(existing -> {
            throw new RuntimeException("Return already exists for item");
        });

        if (orderItem.getQuantity() == null || orderItem.getPriceAtPurchase() == null) {
            throw new RuntimeException("Order item is missing pricing data");
        }
        if (orderItem.getQuantity() <= 0) {
            throw new RuntimeException("Order item quantity must be positive");
        }

        Return returnRequest = new Return();
        returnRequest.setOrderItem(orderItem);
        returnRequest.setReason(request.reason());
        returnRequest.setStatus(ReturnStatus.REQUESTED.name());
        returnRequest.setRefundAmount(orderItem.getPriceAtPurchase() * orderItem.getQuantity());
        returnRequest.setProcessedAt(LocalDateTime.now());
        return returnRepository.save(returnRequest);
    }

    public List<Return> getReturnsForUser(Long userId) {
        return returnRepository.findByOrderItemOrderUserUserId(userId);
    }

    public Return approveReturn(Long returnId) {
        Return returnEntity = getReturn(returnId);
        ensureStatus(returnEntity, ReturnStatus.REQUESTED);
        returnEntity.setStatus(ReturnStatus.APPROVED.name());
        returnEntity.setProcessedAt(LocalDateTime.now());
        return returnRepository.save(returnEntity);
    }

    public Return rejectReturn(Long returnId) {
        Return returnEntity = getReturn(returnId);
        ensureStatus(returnEntity, ReturnStatus.REQUESTED);
        returnEntity.setStatus(ReturnStatus.REJECTED.name());
        returnEntity.setProcessedAt(LocalDateTime.now());
        return returnRepository.save(returnEntity);
    }

    @Transactional
    public Return refundReturn(Long returnId, Long userId, String idempotencyKey) {
        Return returnEntity = getReturn(returnId);
        validateCaller(returnEntity, userId);

        if (ReturnStatus.REFUNDED.name().equals(returnEntity.getStatus())) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                validateRefundRequestHash(returnId, userId, idempotencyKey);
            }
            return returnEntity;
        }
        if (!ReturnStatus.APPROVED.name().equals(returnEntity.getStatus())) {
            throw new RuntimeException("Return must be approved before refund");
        }

        userId = userId == null ? -1L : userId;
        validateRefundRequestHash(returnId, userId, idempotencyKey);

        Long orderId = returnEntity.getOrderItem().getOrder().getOrderId();
        Integer quantity = quantityFor(returnEntity);
        if (quantity > 0) {
            inventoryRepository.findForUpdate(
                    productIdFor(returnEntity.getOrderItem()),
                    variantIdFor(returnEntity.getOrderItem())
            ).ifPresent(inventory -> {
                inventory.setQuantity((inventory.getQuantity() == null ? 0 : inventory.getQuantity()) + quantity);
                inventoryRepository.save(inventory);
            });
        }

        paymentService.refundPayment(orderId, returnEntity.getRefundAmount());
        returnEntity.setStatus(ReturnStatus.REFUNDED.name());
        returnEntity.setProcessedAt(LocalDateTime.now());
        Return saved = returnRepository.save(returnEntity);

        saveIdempotencyKey(userId, idempotencyKey, returnEntity.getReturnId(), orderId);

        Order order = returnEntity.getOrderItem().getOrder();
        order.setStatus(OrderStatus.RETURNED.name());
        orderRepository.save(order);
        return saved;
    }

    private void saveIdempotencyKey(Long userId, String idempotencyKey, Long returnId, Long orderId) {
        String requestHash = "%d:%d".formatted(userId, returnId);
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (hasIdempotencyKey) {
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
    }

    private void validateCaller(Return returnEntity, Long userId) {
        if (returnEntity.getOrderItem() == null
                || returnEntity.getOrderItem().getOrder() == null
                || returnEntity.getOrderItem().getOrder().getUser() == null) {
            throw new RuntimeException("Return not found");
        }

        Long orderUserId = returnEntity.getOrderItem().getOrder().getUser().getUserId();
        if (orderUserId == null || !orderUserId.equals(userId)) {
            throw new RuntimeException("Return not found");
        }
    }

    private void validateRefundRequestHash(Long returnId, Long userId, String idempotencyKey) {
        String requestHash = "%d:%d".formatted(userId, returnId);
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (!hasIdempotencyKey) {
            return;
        }

        Optional<IdempotencyKey> existingKey = idempotencyKeyRepository
                .findByKeyValueAndOperation(idempotencyKey, IDEMPOTENCY_OPERATION);
        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();
            if (!Objects.equals(userId, key.getUserId())) {
                throw new RuntimeException("Idempotency key belongs to another user");
            }
            if (!requestHash.equals(key.getRequestHash())) {
                throw new RuntimeException("Duplicate idempotency key with different request");
            }
        }
    }

    private static int quantityFor(Return returnEntity) {
        return returnEntity.getOrderItem() == null
                || returnEntity.getOrderItem().getQuantity() == null
                ? 0
                : returnEntity.getOrderItem().getQuantity();
    }

    private static Long productIdFor(OrderItem orderItem) {
        return orderItem.getProduct() == null ? null : orderItem.getProduct().getProductId();
    }

    private static Long variantIdFor(OrderItem orderItem) {
        return orderItem.getVariant() == null ? null : orderItem.getVariant().getVariantId();
    }

    private static OrderStatus parseOrderStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return OrderStatus.DRAFT;
        }
        return OrderStatus.valueOf(raw);
    }

    private static boolean isReturnable(OrderStatus status) {
        return switch (status) {
            case DELIVERED, COMPLETED -> true;
            default -> false;
        };
    }

    private Return getReturn(Long returnId) {
        return returnRepository.findById(returnId)
                .orElseThrow(() -> new RuntimeException("Return not found"));
    }

    private void ensureStatus(Return returnEntity, ReturnStatus required) {
        if (!required.name().equals(returnEntity.getStatus())) {
            throw new RuntimeException("Return is not in " + required.name() + " state");
        }
    }
}
