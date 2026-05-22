package com.nyasha.store.dtos.order;

public record ShipOrderRequest(String trackingNumber, String carrier) {
}
