package com.nyasha.store.services;

import com.nyasha.store.entities.IdempotencyKey;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Return;
import com.nyasha.store.entities.User;
import com.nyasha.store.enums.OrderStatus;
import com.nyasha.store.enums.ReturnStatus;
import com.nyasha.store.repositories.IdempotencyKeyRepository;
import com.nyasha.store.repositories.InventoryRepository;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.ReturnRepository;
import com.nyasha.store.services.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    private static final Long RETURN_ID = 42L;
    private static final Long ORDER_ID = 99L;
    private static final Long USER_ID = 7L;
    private static final String IDEMPOTENCY_KEY = "return-key-1";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReturnRepository returnRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private ReturnService returnService;

    @Test
    void refundReturnCompletesAndPersistsIdempotencyKey() {
        Return returnEntity = returnEntity(ReturnStatus.APPROVED, 25.0);
        when(returnRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnEntity));
        when(idempotencyKeyRepository.findByKeyValueAndOperation(IDEMPOTENCY_KEY, ReturnService.IDEMPOTENCY_OPERATION))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findForUpdate(any(), any())).thenReturn(Optional.empty());
        when(returnRepository.save(any(Return.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var refunded = returnService.refundReturn(RETURN_ID, USER_ID, IDEMPOTENCY_KEY);

        assertThat(refunded.getStatus()).isEqualTo(ReturnStatus.REFUNDED.name());
        verify(paymentService).refundPayment(ORDER_ID, 25.0);
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    void refundReturnWithSameIdempotencyKeyReturnsResultWithoutCallingPaymentTwice() {
        Return returnEntity = returnEntity(ReturnStatus.APPROVED, 25.0);
        AtomicReference<IdempotencyKey> capturedKey = new AtomicReference<>();
        when(returnRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnEntity));
        when(idempotencyKeyRepository.findByKeyValueAndOperation(IDEMPOTENCY_KEY, ReturnService.IDEMPOTENCY_OPERATION))
                .thenAnswer(invocation -> Optional.ofNullable(capturedKey.get()));
        when(inventoryRepository.findForUpdate(any(), any())).thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenAnswer(invocation -> {
            IdempotencyKey key = invocation.getArgument(0);
            capturedKey.set(key);
            return key;
        });
        when(returnRepository.save(any(Return.class))).thenAnswer(invocation -> {
            Return saved = invocation.getArgument(0);
            saved.setStatus(ReturnStatus.REFUNDED.name());
            saved.setProcessedAt(LocalDateTime.now());
            return saved;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Return first = returnService.refundReturn(RETURN_ID, USER_ID, IDEMPOTENCY_KEY);
        Return second = returnService.refundReturn(RETURN_ID, USER_ID, IDEMPOTENCY_KEY);

        assertThat(first.getStatus()).isEqualTo(ReturnStatus.REFUNDED.name());
        assertThat(second.getStatus()).isEqualTo(ReturnStatus.REFUNDED.name());
        verify(paymentService, times(1)).refundPayment(ORDER_ID, 25.0);
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
        assertThat(capturedKey.get()).isNotNull();
    }

    @Test
    void refundReturnRejectsIdempotencyKeyFromDifferentUser() {
        Return returnEntity = returnEntity(ReturnStatus.APPROVED, 25.0);
        IdempotencyKey otherUserKey = idempotencyKey(99L, "7:42");
        when(returnRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnEntity));
        when(idempotencyKeyRepository.findByKeyValueAndOperation(IDEMPOTENCY_KEY, ReturnService.IDEMPOTENCY_OPERATION))
            .thenReturn(Optional.of(otherUserKey));

        assertThatThrownBy(() -> returnService.refundReturn(RETURN_ID, USER_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Idempotency key belongs to another user");

        verify(paymentService, never()).refundPayment(ORDER_ID, 25.0);
    }

    @Test
    void refundReturnRejectsDuplicateKeyWithDifferentRequest() {
        Return returnEntity = returnEntity(ReturnStatus.APPROVED, 25.0);
        IdempotencyKey mismatched = idempotencyKey(USER_ID, "7:99");
        when(returnRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnEntity));
        when(idempotencyKeyRepository.findByKeyValueAndOperation(IDEMPOTENCY_KEY, ReturnService.IDEMPOTENCY_OPERATION))
            .thenReturn(Optional.of(mismatched));

        assertThatThrownBy(() -> returnService.refundReturn(RETURN_ID, USER_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate idempotency key with different request");

        verify(paymentService, never()).refundPayment(ORDER_ID, 25.0);
    }

    private Return returnEntity(ReturnStatus status, Double refundAmount) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(8L);
        item.setOrder(order());
        item.setPriceAtPurchase(12.5);
        item.setQuantity(2);

        Return returnEntity = new Return();
        returnEntity.setReturnId(RETURN_ID);
        returnEntity.setOrderItem(item);
        returnEntity.setStatus(status.name());
        returnEntity.setRefundAmount(refundAmount);
        return returnEntity;
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.PAID.name());
        order.setUser(user());
        return order;
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        return user;
    }

    private IdempotencyKey idempotencyKey(Long userId, String requestHash) {
        IdempotencyKey key = new IdempotencyKey();
        key.setKeyValue(IDEMPOTENCY_KEY);
        key.setOperation(ReturnService.IDEMPOTENCY_OPERATION);
        key.setUserId(userId);
        key.setOrderId(ORDER_ID);
        key.setRequestHash(requestHash);
        key.setCreatedAt(LocalDateTime.now());
        key.setExpiresAt(LocalDateTime.now().plusHours(24));
        return key;
    }

}
