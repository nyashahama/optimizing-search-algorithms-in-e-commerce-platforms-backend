package com.nyasha.store.exceptions;

public record ApiError(
        String error,
        String message,
        String path
) { }

