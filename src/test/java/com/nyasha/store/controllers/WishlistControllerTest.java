package com.nyasha.store.controllers;

import com.nyasha.store.dtos.wishlist.AddWishlistItemRequest;
import com.nyasha.store.entities.User;
import com.nyasha.store.entities.Wishlist;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

class WishlistControllerTest {

    private final WishlistService wishlistService = mock(WishlistService.class);
    private final UserService userService = mock(UserService.class);
    private final WishlistController wishlistController = new WishlistController(wishlistService, userService);

    @Test
    void wishlistRoutesDelegateToService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        Wishlist expected = new Wishlist();
        AddWishlistItemRequest addRequest = new AddWishlistItemRequest(1L, null);

        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(7L)));
        when(wishlistService.getOrCreate(7L)).thenReturn(expected);
        when(wishlistService.addItem(7L, addRequest)).thenReturn(expected);

        assertThat(wishlistController.myWishlist(auth)).isSameAs(expected);
        assertThat(wishlistController.add(auth, addRequest).getBody()).isSameAs(expected);
    }

    @Test
    void removeDelegatesToService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(7L)));

        assertThat(wishlistController.remove(auth, 88L).getStatusCode().value()).isEqualTo(204);
    }

    private User user(long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
