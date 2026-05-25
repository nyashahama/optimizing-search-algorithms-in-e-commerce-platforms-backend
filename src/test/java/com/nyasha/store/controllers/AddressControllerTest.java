package com.nyasha.store.controllers;

import com.nyasha.store.entities.Address;
import com.nyasha.store.services.AddressService;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddressControllerTest {

    private final AddressService addressService = mock(AddressService.class);
    private final UserService userService = mock(UserService.class);
    private final AddressController addressController = new AddressController(addressService, userService);

    @Test
    void addressCrudRoutesDelegateToAddressService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        Address address = address(1L, "12 Commerce St", "Cape Town");

        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(7L)));
        when(addressService.createAddress(7L, address)).thenReturn(address);
        when(addressService.getAddressByIdForUser(7L, 1L)).thenReturn(Optional.of(address));
        when(addressService.getAllAddressesForUser(7L)).thenReturn(List.of(address));
        when(addressService.updateAddress(7L, 1L, address)).thenReturn(address);

        ResponseEntity<Address> created = addressController.createAddress(auth, address);
        ResponseEntity<Address> readById = addressController.getAddressById(auth, 1L);
        List<Address> all = addressController.getAllAddresses(auth);
        ResponseEntity<Address> updated = addressController.updateAddress(auth, 1L, address);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isSameAs(address);
        assertThat(readById.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readById.getBody()).isSameAs(address);
        assertThat(all).containsExactly(address);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isSameAs(address);

        assertThat(addressController.deleteAddress(auth, 1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getAddressByIdReturnsNotFoundForMissingAddress() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(7L)));
        when(addressService.getAddressByIdForUser(7L, 1L)).thenReturn(Optional.empty());

        ResponseEntity<Address> response = addressController.getAddressById(auth, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unknownUserCannotBeResolved() {
        Authentication auth = new UsernamePasswordAuthenticationToken("missing@example.com", "secret");
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressController.createAddress(auth, address(1L, "street", "city")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authenticated user not found");
    }

    private Address address(Long id, String street, String city) {
        Address address = new Address();
        address.setAddressId(id);
        address.setStreet(street);
        address.setCity(city);
        address.setCountry("South Africa");
        return address;
    }

    private com.nyasha.store.entities.User user(Long id) {
        com.nyasha.store.entities.User u = new com.nyasha.store.entities.User();
        u.setUserId(id);
        return u;
    }
}
