package com.nyasha.store.dtos.returns;

public record CreateReturnRequest(Long orderItemId, String reason) {
}
