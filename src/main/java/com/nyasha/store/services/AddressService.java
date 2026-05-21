package com.nyasha.store.services;


import com.nyasha.store.entities.Address;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.AddressRepository;
import com.nyasha.store.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AddressService {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    // Create an address for a user
    public Address createAddress(Long userId, Address address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        address.setUser(user);
        return addressRepository.save(address);
    }

    // Read an address by ID
    public Optional<Address> getAddressById(Long id) {
        return addressRepository.findById(id);
    }

    // Read all addresses
    public List<Address> getAllAddresses() {
        return addressRepository.findAll();
    }

    // Update an address
    public Address updateAddress(Long id, Address addressDetails) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        address.setStreet(addressDetails.getStreet());
        address.setCity(addressDetails.getCity());
        address.setState(addressDetails.getState());
        address.setCountry(addressDetails.getCountry());
        address.setZip(addressDetails.getZip());
        return addressRepository.save(address);
    }

    // Delete an address
    public void deleteAddress(Long id) {
        addressRepository.deleteById(id);
    }

}
