package com.reloop.repair.client;

/**
 * repair-service -> order-service REST contract. order-service owns quotes (fee rates, revisions) and
 * payments; repair-service owns the repair request and its status. There is no shared database.
 * Failures surface as {@link com.reloop.repair.exception.OrderServiceException} (503 unreachable,
 * 409 when order-service refuses the state change, 502 otherwise).
 */
public interface OrderServiceClient {

    QuoteView createQuote(CreateQuoteCommand command);

    QuoteView approveQuote(Long quoteId, QuoteDecisionCommand command);

    QuoteView rejectQuote(Long quoteId, QuoteDecisionCommand command);
}
