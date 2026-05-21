package com.nyasha.store.controllers;

import com.nyasha.store.dtos.LoginRequest;
import com.nyasha.store.dtos.UserResponse;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UserController controller = new UserController(userService);

    @Test
    void registerReturnsSanitizedUserWithoutMutatingEntityPassword() {
        User savedUser = user(1L, "Nyasha", "nyasha@example.com", "hashed-password");
        when(userService.createUser(savedUser)).thenReturn(savedUser);

        ResponseEntity<UserResponse> response = controller.createUser(savedUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(new UserResponse(1L, "Nyasha", "nyasha@example.com", null));
        assertThat(savedUser.getHashedPassword()).isEqualTo("hashed-password");
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() {
        LoginRequest request = new LoginRequest("nyasha@example.com", "bad-password");
        when(userService.authenticateUser("nyasha@example.com", "bad-password")).thenReturn(Optional.empty());

        ResponseEntity<UserResponse> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void listUsersReturnsSanitizedResponses() {
        when(userService.getAllUsers()).thenReturn(List.of(user(1L, "Nyasha", "nyasha@example.com", "hash")));

        List<UserResponse> users = controller.getAllUsers(null);

        assertThat(users).containsExactly(new UserResponse(1L, "Nyasha", "nyasha@example.com", null));
    }

    private User user(Long id, String name, String email, String password) {
        User user = new User();
        user.setUserId(id);
        user.setName(name);
        user.setEmail(email);
        user.setHashedPassword(password);
        return user;
    }
}
