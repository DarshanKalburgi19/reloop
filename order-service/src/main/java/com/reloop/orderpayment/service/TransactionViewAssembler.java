package com.reloop.orderpayment.service;

import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.dto.refund.RefundView;
import com.reloop.orderpayment.entity.CommissionRecord;
import com.reloop.orderpayment.entity.PaymentTransaction;
import com.reloop.orderpayment.entity.ProviderPayout;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.repository.CommissionRecordRepository;
import com.reloop.orderpayment.repository.ProviderPayoutRepository;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.repository.RefundRecordRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the single {@link TransactionView} shape used by every payment endpoint and dashboard. */
@Component
public class TransactionViewAssembler {

    private final PurchaseOrderRepository orders;
    private final RepairQuoteRepository quotes;
    private final CommissionRecordRepository commissions;
    private final ProviderPayoutRepository payouts;
    private final RefundRecordRepository refunds;

    public TransactionViewAssembler(PurchaseOrderRepository orders, RepairQuoteRepository quotes,
                                    CommissionRecordRepository commissions, ProviderPayoutRepository payouts,
                                    RefundRecordRepository refunds) {
        this.orders = orders;
        this.quotes = quotes;
        this.commissions = commissions;
        this.payouts = payouts;
        this.refunds = refunds;
    }

    public TransactionView view(PaymentTransaction p) {
        PurchaseOrder order = p.getPurchaseOrderId() == null ? null : orders.findById(p.getPurchaseOrderId()).orElse(null);
        RepairQuote quote = p.getRepairQuoteId() == null ? null : quotes.findById(p.getRepairQuoteId()).orElse(null);
        CommissionRecord c = commissions.findByPaymentTransactionId(p.getId()).orElse(null);
        ProviderPayout payout = payouts.findByPaymentTransactionId(p.getId()).orElse(null);
        List<RefundView> refundViews = refunds.findByPaymentTransactionIdOrderByIdAsc(p.getId()).stream()
                .map(r -> RefundView.of(r, p.getTransactionId())).toList();

        long reversedNet = payout == null ? 0 : payout.getReversedNetPaise();
        long reversedRevenue = c == null ? 0 : c.getReversedCustomerFeePaise() + c.getReversedProviderCommissionPaise();
        boolean settled = p.getStatus().isSettled();
        long currentNet = settled ? p.getProviderNetPaise() - reversedNet : 0;
        long currentRevenue = settled ? p.getPlatformRevenuePaise() - reversedRevenue : 0;

        return new TransactionView(p.getId(), p.getTransactionId(), p.getTransactionType().name(), p.getStatus().name(),
                p.getMethod().name(), true, p.getPurchaseOrderId(), order == null ? null : order.getOrderNumber(),
                order == null ? null : order.getProductExternalId(), p.getRepairQuoteId(),
                quote == null ? null : quote.getRepairRequestExternalId(), p.getBuyerAccountId(),
                p.getProviderType().name(), p.getProviderId(), p.getProviderAccountId(), p.getBaseAmountPaise(),
                p.getCustomerFeeRate(), p.getCustomerFeePaise(), p.getTotalChargedPaise(), p.getProviderCommissionRate(),
                p.getProviderCommissionPaise(), p.getProviderNetPaise(), p.getPlatformRevenuePaise(),
                p.getRefundedAmountPaise(), reversedNet, reversedRevenue, currentNet, currentRevenue, p.getCurrency(),
                p.getFailureReason(), p.getAttemptedAt(), p.getCompletedAt(),
                c == null ? null : c.getTransactionId(), c == null ? null : c.getStatus().name(),
                payout == null ? null : payout.getPayoutReference(), payout == null ? null : payout.getStatus().name(),
                refundViews, "/api/transactions/" + p.getTransactionId() + "/receipt", TransactionView.SIMULATION_NOTE);
    }
}
