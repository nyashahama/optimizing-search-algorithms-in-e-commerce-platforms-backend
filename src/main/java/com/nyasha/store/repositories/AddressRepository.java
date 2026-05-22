package com.nyasha.store.repositories;

import com.nyasha.store.entities.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address,Long> {
    Optional<Address> findByAddressIdAndUserUserId(Long addressId, Long userId);

    List<Address> findByUserUserId(Long userId);
}
