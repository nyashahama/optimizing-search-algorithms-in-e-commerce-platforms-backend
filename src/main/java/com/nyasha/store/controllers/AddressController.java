package com.nyasha.store.controllers;


import com.nyasha.store.entities.Address;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.AddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService addressService;
    private final UserService userService;

    public AddressController(AddressService addressService, UserService userService) {
        this.addressService = addressService;
        this.userService = userService;
    }

    // Create an address
    @PostMapping("/me")
    public ResponseEntity<Address> createAddress(Authentication authentication, @RequestBody Address address) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.createAddress(currentUserId(authentication), address));
    }

    // Read an address by ID
    @GetMapping("/me/{id}")
    public ResponseEntity<Address> getAddressById(Authentication authentication, @PathVariable Long id) {
        return addressService.getAddressByIdForUser(currentUserId(authentication), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Read all addresses
    @GetMapping("/me")
    public List<Address> getAllAddresses(Authentication authentication) {
        return addressService.getAllAddressesForUser(currentUserId(authentication));
    }

    // Update an address
    @PutMapping("/me/{id}")
    public ResponseEntity<Address> updateAddress(Authentication authentication, @PathVariable Long id, @RequestBody Address addressDetails) {
        return ResponseEntity.ok(addressService.updateAddress(currentUserId(authentication), id, addressDetails));
    }

    // Delete an address
    @DeleteMapping("/me/{id}")
    public ResponseEntity<Void> deleteAddress(Authentication authentication, @PathVariable Long id) {
        addressService.deleteAddress(currentUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
