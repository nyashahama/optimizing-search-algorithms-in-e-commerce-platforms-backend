package com.nyasha.store.controllers;

import com.nyasha.store.dtos.cart.AddCartItemRequest;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.CartService;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartControllerTest {

    private final CartService cartService = mock(CartService.class);
    private final UserService userService = mock(UserService.class);
    private final CartController controller = new CartController(cartService, userService);

    @Test
    void getCurrentCartUsesAuthenticatedUserId() {
        Cart expected = new Cart();
        expected.setCartId(10L);

        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(1L)));
        when(cartService.getForUser(1L)).thenReturn(expected);

        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");

        Cart cart = controller.getCurrentCart(auth);

        assertThat(cart).isEqualTo(expected);
        verify(cartService).getForUser(1L);
    }

    @Test
    void addItemDelegatesToService() {
        Cart expected = new Cart();
        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(1L)));
        when(cartService.addItem(1L, new AddCartItemRequest(9L, null, 2))).thenReturn(expected);

        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");

        assertThat(controller.addItem(auth, new AddCartItemRequest(9L, null, 2)).getBody())
                .isSameAs(expected);
    }

    @Test
    void unknownUserInAuthenticationIsRejected() {
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());
        Authentication auth = new UsernamePasswordAuthenticationToken("missing@example.com", "secret");

        assertThatThrownBy(() -> controller.getCurrentCart(auth))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authenticated user not found");
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}

