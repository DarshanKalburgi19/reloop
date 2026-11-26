package com.reloop.orderpayment.dto.order;

import jakarta.validation.constraints.Size;

/** Optional body of {@code POST /api/orders/{id}/cancel}. */
public record CancelOrderRequest(@Size(max = 500) String reason) {
}
