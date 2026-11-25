package com.reloop.product.dto.product;

import com.reloop.product.enums.ProductHistoryEventType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One entry of the public passport timeline. To protect privacy, the actor appears only as
 * a role relative to the listing ({@code SELLER} or {@code BUYER}), never by name or email.
 */
public record PassportEventResponse(
        ProductHistoryEventType eventType,
        LocalDateTime occurredAt,
        Long productId,
        String actorRole,
        BigDecimal price,
        String details) {
}
