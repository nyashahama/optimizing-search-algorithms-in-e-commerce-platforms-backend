package com.nyasha.store.controllers;

import com.nyasha.store.dtos.LoginRequest;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@CrossOrigin({"http://localhost:3000", "http://localhost:4200"})
public class UserController {

    @Autowired
    private UserService userService;

    // Create a user.
    @PostMapping("/register")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User created = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(sanitizeUser(created));
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody LoginRequest loginRequest) {
        Optional<User> user = userService.authenticateUser(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );

        return user.map(this::sanitizeUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // Read a user by ID.
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(this::sanitizeUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get all users or search by a term.
    @GetMapping
    public List<User> getAllUsers(@RequestParam(required = false) String search) {
        if (search != null && !search.isEmpty()) {
            return userService.searchUsers(search).stream()
                    .map(this::sanitizeUser)
                    .collect(Collectors.toList());
        } else {
            return userService.getAllUsers().stream()
                    .map(this::sanitizeUser)
                    .collect(Collectors.toList());
        }
    }

    // Alternative search endpoint.
    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam String query) {
        return userService.searchUsers(query).stream()
                .map(this::sanitizeUser)
                .collect(Collectors.toList());
    }

    // Update a user.
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return sanitizeUser(userService.updateUser(id, userDetails));
    }

    // Delete a user.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    private User sanitizeUser(User user) {
        if (user == null) {
            return null;
        }
        user.setHashedPassword(null);
        return user;
    }
}
