package com.nyasha.store.repositories;

import com.nyasha.store.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    List<User> findByNameContainingIgnoreCase(String searchTerm);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);
}
