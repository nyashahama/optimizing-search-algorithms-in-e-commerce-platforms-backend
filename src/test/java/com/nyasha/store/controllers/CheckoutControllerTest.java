package com.nyasha.store.controllers;

import com.nyasha.store.dtos.checkout.CheckoutConfirmRequest;
import com.nyasha.store.dtos.checkout.CheckoutConfirmResponse;
import com.nyasha.store.dtos.checkout.CheckoutPreviewRequest;
import com.nyasha.store.dtos.checkout.CheckoutPreviewResponse;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.CheckoutService;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutControllerTest {

    private final CheckoutService checkoutService = mock(CheckoutService.class);
    private final UserService userService = mock(UserService.class);
    private final CheckoutController checkoutController = new CheckoutController(checkoutService, userService);

    @Test
    void checkoutEndpointsDelegateToService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        CheckoutPreviewRequest previewRequest = new CheckoutPreviewRequest(null, null, null, 42L);
        CheckoutConfirmRequest confirmRequest = new CheckoutConfirmRequest("SIMULATED", null, null, null, 42L);

        CheckoutPreviewResponse preview = new CheckoutPreviewResponse(10.0, 0.0, 2.0, 1.0, 13.0, List.of());
        CheckoutConfirmResponse confirm = new CheckoutConfirmResponse(
                1L, "PAID", 10.0, 0.0, 0.0, 1.0, 11.0,
                "CAPTURED", List.of()
        );

        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(4L)));
        when(checkoutService.preview(4L, previewRequest)).thenReturn(preview);
        when(checkoutService.confirm(4L, confirmRequest, "idem-key")).thenReturn(confirm);

        assertThat(checkoutController.preview(auth, previewRequest)).isSameAs(preview);
        assertThat(checkoutController.confirm(auth, "idem-key", confirmRequest).getBody()).isSameAs(confirm);
    }

    private User user(long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
