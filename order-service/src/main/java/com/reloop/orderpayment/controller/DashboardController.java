package com.reloop.orderpayment.controller;

import com.reloop.orderpayment.dto.report.CustomerPaymentsView;
import com.reloop.orderpayment.dto.report.ProviderEarningsView;
import com.reloop.orderpayment.dto.report.ProviderPayoutsView;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import com.reloop.orderpayment.service.TransactionQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard data. {@code {id}} is a product-service Account ID and must be the caller's own (403 otherwise).
 *
 * <pre>
 * GET /api/customers/{id}/payments   every payment attempt of the customer + totals
 * GET /api/providers/{id}/earnings   seller / repairer earnings (gross, commission, net, refunds), per type
 * GET /api/providers/{id}/payouts    simulated payouts
 * </pre>
 * Sellers are CUSTOMER accounts and repairers are REPAIRER accounts; both use the provider endpoints with their own
 * account id.
 */
@RestController
public class DashboardController {

    private final TransactionQueryService queries;

    public DashboardController(TransactionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping(path = "/api/customers/{id:\\d+}/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    public CustomerPaymentsView customerPayments(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable Long id) {
        return queries.customerPayments(caller, id);
    }

    @GetMapping(path = "/api/providers/{id:\\d+}/earnings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderEarningsView earnings(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable Long id) {
        return queries.providerEarnings(caller, id);
    }

    @GetMapping(path = "/api/providers/{id:\\d+}/payouts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderPayoutsView payouts(@AuthenticationPrincipal AuthenticatedAccount caller, @PathVariable Long id) {
        return queries.providerPayouts(caller, id);
    }
}
