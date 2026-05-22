package com.nyasha.store.services;

import com.nyasha.store.dtos.wishlist.AddWishlistItemRequest;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.ProductVariant;
import com.nyasha.store.entities.User;
import com.nyasha.store.entities.Wishlist;
import com.nyasha.store.entities.WishlistItem;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ProductVariantRepository;
import com.nyasha.store.repositories.UserRepository;
import com.nyasha.store.repositories.WishlistItemRepository;
import com.nyasha.store.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WishlistService wishlistService;

    @Test
    void getOrCreateReturnsExistingWishlist() {
        Wishlist wishlist = new Wishlist();
        when(wishlistRepository.findByUserUserId(1L)).thenReturn(Optional.of(wishlist));

        Wishlist actual = wishlistService.getOrCreate(1L);

        assertThat(actual).isSameAs(wishlist);
    }

    @Test
    void addItemCreatesWishlistAndItem() {
        User user = user(1L);
        Product product = product(10L);
        when(wishlistRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Wishlist result = wishlistService.addItem(1L, new AddWishlistItemRequest(10L, null));

        assertThat(result.getWishlistItems()).hasSize(1);
    }

    @Test
    void addItemRejectsDuplicate() {
        Wishlist wishlist = new Wishlist();
        wishlist.setWishlistId(9L);
        wishlist.setWishlistItems(new HashSet<>());
        when(wishlistRepository.findByUserUserId(1L)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product(10L)));
        when(wishlistItemRepository.findByWishlistWishlistIdAndProductProductIdAndVariantVariantId(9L, 10L, null))
                .thenReturn(Optional.of(new WishlistItem()));

        assertThatThrownBy(() -> wishlistService.addItem(1L, new AddWishlistItemRequest(10L, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already in wishlist");
    }

    @Test
    void removeItemDeletesWishlistItem() {
        Wishlist wishlist = new Wishlist();
        wishlist.setWishlistId(9L);
        WishlistItem item = new WishlistItem();
        item.setWishlistItemId(8L);
        wishlist.getWishlistItems().add(item);
        when(wishlistRepository.findByUserUserId(1L)).thenReturn(Optional.of(wishlist));
        when(wishlistItemRepository.findByWishlistWishlistIdAndWishlistItemId(9L, 8L))
                .thenReturn(Optional.of(item));

        wishlistService.removeItem(1L, 8L);

        verify(wishlistItemRepository).delete(item);
    }

    @Test
    void addItemRejectsInvalidVariantProductMismatch() {
        Product product = product(10L);
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(5L);
        Product otherProduct = product(12L);
        variant.setProduct(otherProduct);

        Wishlist wishlist = wishlist();
        wishlist.setWishlistId(9L);
        when(wishlistRepository.findByUserUserId(1L)).thenReturn(Optional.of(wishlist));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(5L)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> wishlistService.addItem(1L, new AddWishlistItemRequest(10L, 5L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Variant does not belong");
    }

    private Wishlist wishlist() {
        Wishlist wishlist = new Wishlist();
        wishlist.setWishlistItems(new HashSet<>());
        return wishlist;
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setProductId(id);
        return product;
    }
}
