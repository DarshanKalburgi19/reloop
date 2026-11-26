package com.reloop.orderpayment.client;

/**
 * product-service's answer to the internal reserve / confirm-sale / release calls.
 *
 * @param pricePaise           the listing price at reservation time (becomes the order subtotal)
 * @param status               product status after the call (RESERVED, SOLD, ACTIVE, ...)
 * @param reservationReference order number holding the reservation, if any
 */
public record ProductSale(
        Long productId,
        String title,
        Long sellerAccountId,
        Long buyerAccountId,
        long pricePaise,
        String status,
        String passportCode,
        String reservationReference) {
}
