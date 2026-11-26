package com.reloop.orderpayment.service;

import com.reloop.orderpayment.dto.payment.TransactionView;
import com.reloop.orderpayment.dto.refund.RefundView;
import com.reloop.orderpayment.entity.PurchaseOrder;
import com.reloop.orderpayment.repository.PurchaseOrderRepository;
import com.reloop.orderpayment.security.AuthenticatedAccount;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Printable / downloadable SIMULATED receipts for a transaction, as self-contained HTML (print it to PDF from the
 * browser) or plain text. Visible to the buyer and the provider of the transaction only. Every receipt carries a
 * clear "simulated, no money moved" notice and never contains card or bank data (none is stored).
 */
@Service
public class ReceiptService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final String NOTICE = "SIMULATED TRANSACTION - no real money was transferred and no payment "
            + "gateway was used. ReLoop demo receipt; not a tax invoice.";

    private final TransactionQueryService transactions;
    private final PurchaseOrderRepository orders;

    public ReceiptService(TransactionQueryService transactions, PurchaseOrderRepository orders) {
        this.transactions = transactions;
        this.orders = orders;
    }

    /** One labelled line of the receipt. */
    record Line(String label, String value) {
    }

    @Transactional(readOnly = true)
    public String text(AuthenticatedAccount caller, String transactionId) {
        TransactionView t = transactions.transaction(caller, transactionId);
        StringBuilder sb = new StringBuilder();
        String rule = "=".repeat(60) + "\n";
        sb.append(rule).append("ReLoop - ").append(title(t)).append('\n').append(rule);
        for (Line l : header(t)) {
            sb.append(String.format("%-28s %s%n", l.label() + ":", l.value()));
        }
        sb.append("-".repeat(60)).append('\n').append("CUSTOMER\n");
        for (Line l : customerLines(t)) {
            sb.append(String.format("  %-26s %15s%n", l.label(), l.value()));
        }
        sb.append("PROVIDER (").append(t.providerType()).append(")\n");
        for (Line l : providerLines(t)) {
            sb.append(String.format("  %-26s %15s%n", l.label(), l.value()));
        }
        sb.append("RELOOP\n");
        for (Line l : platformLines(t)) {
            sb.append(String.format("  %-26s %15s%n", l.label(), l.value()));
        }
        if (!t.refunds().isEmpty()) {
            sb.append("REFUNDS\n");
            for (RefundView r : t.refunds()) {
                sb.append(String.format("  %s  %s  %s  (%s)%n", r.refundReference(), Money.format(r.refundAmountPaise()),
                        r.reason(), r.completedAt() == null ? r.status() : r.completedAt().format(WHEN)));
            }
        }
        sb.append(rule).append(NOTICE).append('\n');
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String html(AuthenticatedAccount caller, String transactionId) {
        TransactionView t = transactions.transaction(caller, transactionId);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>")
          .append(esc(title(t) + " " + t.transactionId())).append("</title><style>")
          .append("body{font-family:system-ui,Arial,sans-serif;max-width:720px;margin:24px auto;color:#1b1b1b}")
          .append("h1{font-size:22px;margin:0}h2{font-size:15px;margin:18px 0 6px;color:#2e7d32}")
          .append("table{width:100%;border-collapse:collapse}td{padding:4px 6px;border-bottom:1px solid #eee}")
          .append("td.v{text-align:right;font-variant-numeric:tabular-nums}.notice{margin-top:20px;padding:10px;")
          .append("background:#fff8e1;border:1px solid #ffcc80;font-size:13px}.status{font-weight:bold}")
          .append("@media print{.noprint{display:none}}</style></head><body>")
          .append("<h1>ReLoop &middot; ").append(esc(title(t))).append("</h1>")
          .append("<p class=\"noprint\"><button onclick=\"window.print()\">Print / Save as PDF</button></p>");
        table(sb, null, header(t), false);
        table(sb, "Customer", customerLines(t), true);
        table(sb, "Provider (" + t.providerType() + ")", providerLines(t), true);
        table(sb, "ReLoop", platformLines(t), true);
        if (!t.refunds().isEmpty()) {
            List<Line> rows = new ArrayList<>();
            for (RefundView r : t.refunds()) {
                rows.add(new Line(r.refundReference() + " - " + r.reason(), Money.format(r.refundAmountPaise())));
            }
            table(sb, "Refunds", rows, true);
        }
        sb.append("<div class=\"notice\">").append(esc(NOTICE)).append("</div></body></html>");
        return sb.toString();
    }

    private List<Line> header(TransactionView t) {
        List<Line> l = new ArrayList<>();
        l.add(new Line("Transaction ID", t.transactionId()));
        l.add(new Line("Type", t.transactionType()));
        l.add(new Line("Status", t.status()));
        l.add(new Line("Payment method", t.method() + " (simulated)"));
        l.add(new Line("Date", t.completedAt() != null ? t.completedAt().format(WHEN) : t.attemptedAt().format(WHEN)));
        if (t.orderNumber() != null) {
            l.add(new Line("Order", t.orderNumber()));
            orders.findById(t.purchaseOrderId()).map(this::itemTitle).ifPresent(title -> l.add(new Line("Item", title)));
        }
        if (t.repairRequestId() != null) {
            l.add(new Line("Repair request", "#" + t.repairRequestId() + " (quote " + t.repairQuoteId() + ")"));
        }
        l.add(new Line("Customer account", String.valueOf(t.buyerAccountId())));
        l.add(new Line("Provider", t.providerType() + " #" + (t.providerAccountId() != null ? t.providerAccountId() : t.providerId())));
        if (t.failureReason() != null) {
            l.add(new Line("Note", t.failureReason()));
        }
        return l;
    }

    private static List<Line> customerLines(TransactionView t) {
        List<Line> l = new ArrayList<>();
        l.add(new Line("Price", Money.format(t.baseAmountPaise())));
        l.add(new Line("Platform fee (" + Money.percent(t.customerFeeRate()) + ")", Money.format(t.customerFeePaise())));
        l.add(new Line(settled(t) ? "Total paid" : "Total (not charged)", Money.format(t.totalChargedPaise())));
        if (t.refundedAmountPaise() > 0) {
            l.add(new Line("Refunded", Money.format(t.refundedAmountPaise())));
            l.add(new Line("Net paid", Money.format(t.totalChargedPaise() - t.refundedAmountPaise())));
        }
        return l;
    }

    private static List<Line> providerLines(TransactionView t) {
        List<Line> l = new ArrayList<>();
        l.add(new Line("Gross amount", Money.format(t.baseAmountPaise())));
        l.add(new Line("Commission (" + Money.percent(t.providerCommissionRate()) + ")",
                "-" + Money.format(t.providerCommissionPaise())));
        l.add(new Line("Net earnings", Money.format(t.providerNetPaise())));
        if (t.reversedProviderNetPaise() > 0) {
            l.add(new Line("Reversed by refunds", "-" + Money.format(t.reversedProviderNetPaise())));
            l.add(new Line("Current net earnings", Money.format(t.currentProviderNetPaise())));
        }
        return l;
    }

    private static List<Line> platformLines(TransactionView t) {
        List<Line> l = new ArrayList<>();
        l.add(new Line("Customer fee", Money.format(t.customerFeePaise())));
        l.add(new Line("Provider commission", Money.format(t.providerCommissionPaise())));
        l.add(new Line("ReLoop revenue", Money.format(t.platformRevenuePaise())));
        if (t.reversedPlatformRevenuePaise() > 0) {
            l.add(new Line("Reversed by refunds", "-" + Money.format(t.reversedPlatformRevenuePaise())));
            l.add(new Line("Current ReLoop revenue", Money.format(t.currentPlatformRevenuePaise())));
        }
        return l;
    }

    private String itemTitle(PurchaseOrder o) {
        return o.getItems().isEmpty() ? null : o.getItems().get(0).getProductTitleSnapshot();
    }

    private static boolean settled(TransactionView t) {
        return t.status().equals("SUCCESS") || t.status().equals("PARTIALLY_REFUNDED") || t.status().equals("REFUNDED");
    }

    private static String title(TransactionView t) {
        return settled(t) ? "Payment receipt (simulated)" : "Payment attempt - " + t.status() + " (simulated)";
    }

    private static void table(StringBuilder sb, String heading, List<Line> rows, boolean money) {
        if (heading != null) {
            sb.append("<h2>").append(esc(heading)).append("</h2>");
        }
        sb.append("<table>");
        for (Line r : rows) {
            sb.append("<tr><td>").append(esc(r.label())).append("</td><td").append(money ? " class=\"v\"" : "")
              .append(">").append(esc(r.value())).append("</td></tr>");
        }
        sb.append("</table>");
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
