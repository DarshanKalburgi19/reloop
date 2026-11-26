package com.reloop.orderpayment.client;

import com.reloop.orderpayment.exception.RepairServiceException;

/** Calls into repair-service (REST; the two services never share a database). */
public interface RepairServiceClient {

    /**
     * Tells repair-service that the request's approved quote has been paid, moving it to PAID.
     *
     * @throws RepairServiceException 503 unreachable, 409 refused (wrong state/amount/quote), 502 otherwise
     */
    void confirmPayment(Long repairRequestId, PaymentConfirmation confirmation);

    /**
     * Reports a failed / cancelled attempt or a refund to repair-service (idempotent per {@code eventId}).
     * Throws {@link com.reloop.orderpayment.exception.RepairServiceException} (409 / 502 / 503) like
     * {@link #confirmPayment}.
     */
    void reportPaymentEvent(Long repairRequestId, RepairPaymentEvent event);
}
