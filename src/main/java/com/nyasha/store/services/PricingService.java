package com.nyasha.store.services;

import com.nyasha.store.dtos.checkout.CheckoutLineItemResponse;
import com.nyasha.store.dtos.checkout.CheckoutPreviewRequest;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.entities.CartItem;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.ProductVariant;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PricingService {

    public static final String SAVE10_CODE = "SAVE10";
    public static final String SAVE20_CODE = "SAVE20";

    private static final double FREE_SHIPPING_THRESHOLD = 150.0;
    private static final double SHIPMENT_STANDARD = 12.0;
    private static final double TAX_RATE_DEFAULT = 0.08;

    public PricingResult compute(Cart cart, CheckoutPreviewRequest request) {
        if (cart == null) {
            throw new IllegalArgumentException("cart is required");
        }

        List<CheckoutLineItemResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem item : cart.getCartItems()) {
            Product product = item.getProduct();
            ProductVariant variant = item.getVariant();

            BigDecimal unitPrice = toMoney(priceFor(product, variant));
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity());
            BigDecimal lineTotal = unitPrice.multiply(qty);
            subtotal = subtotal.add(lineTotal);

            lines.add(new CheckoutLineItemResponse(
                    product == null ? null : product.getProductId(),
                    variant == null ? null : variant.getVariantId(),
                    product == null ? null : product.getName(),
                    item.getQuantity(),
                    unitPrice.doubleValue(),
                    lineTotal.doubleValue()
            ));
        }

        BigDecimal discountRate = discountRate(request == null ? null : request.couponCode());
        BigDecimal discountAmount = subtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingAmount = resolveShippingCost(request, subtotal);

        BigDecimal taxableBase = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
        BigDecimal taxRate = Optional.ofNullable(request)
                .map(CheckoutPreviewRequest::taxRate)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.valueOf(TAX_RATE_DEFAULT));
        BigDecimal taxAmount = taxableBase.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingAndTaxBase = taxableBase.add(shippingAmount);
        BigDecimal total = shippingAndTaxBase.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        return new PricingResult(
                subtotal.setScale(2, RoundingMode.HALF_UP),
                discountAmount,
                shippingAmount.setScale(2, RoundingMode.HALF_UP),
                taxAmount,
                total,
                lines
        );
    }

    public PricingResult computeWithTotals(Cart cart, String couponCode, Double shippingCost, Double taxRate) {
        return compute(cart, new CheckoutPreviewRequest(couponCode, shippingCost, taxRate, null));
    }

    private BigDecimal freeShippingCharge(BigDecimal subtotal) {
        if (subtotal.compareTo(BigDecimal.valueOf(FREE_SHIPPING_THRESHOLD)) >= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(SHIPMENT_STANDARD);
    }

    private BigDecimal resolveShippingCost(CheckoutPreviewRequest request, BigDecimal subtotal) {
        if (request != null && request.shippingCost() != null) {
            return BigDecimal.valueOf(request.shippingCost()).setScale(2, RoundingMode.HALF_UP);
        }
        return freeShippingCharge(subtotal);
    }

    private BigDecimal discountRate(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        return switch (couponCode.trim().toUpperCase()) {
            case SAVE10_CODE -> BigDecimal.valueOf(0.10);
            case SAVE20_CODE -> BigDecimal.valueOf(0.20);
            default -> BigDecimal.ZERO;
        };
    }

    private double priceFor(Product product, ProductVariant variant) {
        if (product == null) {
            throw new RuntimeException("Product is required");
        }
        double base = product.getBasePrice() == null ? 0.0 : product.getBasePrice();
        double adjustment = variant == null || variant.getPriceAdjustment() == null ? 0.0 : variant.getPriceAdjustment();
        return base + adjustment;
    }

    private BigDecimal toMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record PricingResult(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal shippingAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            List<CheckoutLineItemResponse> lines
    ) {
    }
}
