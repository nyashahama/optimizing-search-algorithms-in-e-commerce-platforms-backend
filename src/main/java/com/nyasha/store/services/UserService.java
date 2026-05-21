package com.nyasha.store.services;

import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.UserRepository;
import com.nyasha.store.utils.UserIndex;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserIndex userIndex;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserIndex userIndex) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userIndex = userIndex;
    }

    @PostConstruct
    public void initializeIndex() {
        try {
            List<User> users = userRepository.findAll();
            for (User user : users) {
                userIndex.insert(user);
            }
            logger.info("User index initialized with {} users", users.size());
        } catch (Exception e) {
            logger.error("Error initializing user index: {}", e.getMessage(), e);
            throw new RuntimeException("User initialization failed: " + e.getMessage());
        }
    }

    public User createUser(User user) {
        try {
            requireUserPayload(user);
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                throw new RuntimeException("Email already in use");
            }

            user.setHashedPassword(passwordEncoder.encode(user.getHashedPassword()));
            User savedUser = userRepository.save(user);
            userIndex.insert(savedUser);
            logger.info("Created user with id {}", savedUser.getUserId());
            return savedUser;
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage(), e);
            throw new RuntimeException("User creation failed: " + e.getMessage());
        }
    }

    /**
     * Authenticate user by email and password.
     */
    public Optional<User> authenticateUser(String email, String password) {
        try {
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return Optional.empty();
            }
            Optional<User> optionalUser = userRepository.findByEmail(email);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                if (user.getHashedPassword() != null && passwordEncoder.matches(password, user.getHashedPassword())) {
                    logger.info("User authenticated with id {}", user.getUserId());
                    return Optional.of(user);
                }
            }
            logger.warn("Authentication failed for email: {}", email);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error during authentication for email {}: {}", email, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public User updateUser(Long id, User userDetails) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            requireUserPayloadForUpdate(userDetails);
            String oldName = user.getName();
            String oldEmail = user.getEmail();

            if (!Objects.equals(user.getEmail(), userDetails.getEmail())
                    && userRepository.findByEmail(userDetails.getEmail()).isPresent()) {
                throw new RuntimeException("Email already in use");
            }

            user.setName(userDetails.getName());
            user.setEmail(userDetails.getEmail());
            if (userDetails.getHashedPassword() != null && !userDetails.getHashedPassword().isEmpty()) {
                user.setHashedPassword(passwordEncoder.encode(userDetails.getHashedPassword()));
            }

            User updatedUser = userRepository.save(user);
            userIndex.update(oldName, oldEmail, updatedUser);
            logger.info("Updated user with id {}", updatedUser.getUserId());
            return updatedUser;
        } catch (RuntimeException e) {
            logger.error("Error updating user with id {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("User update failed: " + e.getMessage());
        }
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(Long id) {
        try {
            Optional<User> optionalUser = userRepository.findById(id);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                userIndex.remove(user);
                userRepository.deleteById(id);
                logger.info("Deleted user with id {}", id);
            } else {
                logger.warn("Attempted to delete non-existing user with id {}", id);
            }
        } catch (Exception e) {
            logger.error("Error deleting user with id {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Deleting user failed: " + e.getMessage());
        }
    }

    public List<User> searchUsers(String searchTerm) {
        try {
            logger.info("Searching for users with search term: {}", searchTerm);
            List<User> results = userIndex.search(searchTerm);
            logger.info("Found {} users matching search term '{}'", results.size(), searchTerm);
            return results;
        } catch (Exception e) {
            logger.error("Search error for term '{}': {}", searchTerm, e.getMessage(), e);
            throw new RuntimeException("Search failed: " + e.getMessage());
        }
    }

    private void requireUserPayload(User user) {
        if (user == null) {
            throw new RuntimeException("User payload is required");
        }
        if (user.getName() == null || user.getName().isBlank()) {
            throw new RuntimeException("User name is required");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new RuntimeException("User email is required");
        }
        if (user.getHashedPassword() == null || user.getHashedPassword().isBlank()) {
            throw new RuntimeException("User password is required");
        }
    }

    private void requireUserPayloadForUpdate(User user) {
        if (user == null) {
            throw new RuntimeException("User payload is required");
        }
        if (user.getName() == null || user.getName().isBlank()) {
            throw new RuntimeException("User name is required");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new RuntimeException("User email is required");
        }
    }
}
