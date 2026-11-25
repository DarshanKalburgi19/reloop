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
}
