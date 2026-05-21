package com.nyasha.store.dtos;

import com.nyasha.store.entities.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String name,
        String email,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
