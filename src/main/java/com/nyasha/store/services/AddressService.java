package com.nyasha.store.services;


import com.nyasha.store.entities.Address;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.AddressRepository;
import com.nyasha.store.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AddressService {
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    // Create an address for a user
    public Address createAddress(Long userId, Address address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        address.setUser(user);
        validateAddress(address);
        return addressRepository.save(address);
    }

    // Read an address by ID
    public Optional<Address> getAddressByIdForUser(Long userId, Long id) {
        return addressRepository.findByAddressIdAndUserUserId(id, userId);
    }

    // Read all addresses
    public List<Address> getAllAddressesForUser(Long userId) {
        return addressRepository.findByUserUserId(userId);
    }

    // Update an address
    public Address updateAddress(Long userId, Long id, Address addressDetails) {
        Address address = addressRepository.findByAddressIdAndUserUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        address.setStreet(addressDetails.getStreet());
        address.setCity(addressDetails.getCity());
        address.setState(addressDetails.getState());
        address.setCountry(addressDetails.getCountry());
        address.setZip(addressDetails.getZip());
        validateAddress(address);
        return addressRepository.save(address);
    }

    // Delete an address
    public void deleteAddress(Long userId, Long id) {
        Address address = addressRepository.findByAddressIdAndUserUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        addressRepository.deleteById(address.getAddressId());
    }

    public Address validateOwnership(Long userId, Long addressId) {
        return addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
    }

    private void validateAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Address payload is required");
        }
        if (address.getStreet() == null || address.getStreet().isBlank()) {
            throw new IllegalArgumentException("Street is required");
        }
        if (address.getCity() == null || address.getCity().isBlank()) {
            throw new IllegalArgumentException("City is required");
        }
        if (address.getCountry() == null || address.getCountry().isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
    }

}
