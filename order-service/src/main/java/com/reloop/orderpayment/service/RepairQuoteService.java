package com.reloop.orderpayment.service;

import com.reloop.orderpayment.dto.quote.CreateRepairQuoteRequest;
import com.reloop.orderpayment.dto.quote.QuoteDecisionRequest;
import com.reloop.orderpayment.dto.quote.RepairQuoteView;
import com.reloop.orderpayment.entity.RepairQuote;
import com.reloop.orderpayment.enums.PaymentStatus;
import com.reloop.orderpayment.enums.QuoteStatus;
import com.reloop.orderpayment.exception.ConflictException;
import com.reloop.orderpayment.exception.NotFoundException;
import com.reloop.orderpayment.repository.PaymentTransactionRepository;
import com.reloop.orderpayment.repository.RepairQuoteRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repair quotes: order-service owns the money side (fee math, frozen rates, revisions, approval state);
 * repair-service owns the repair request and its workflow and calls these methods over REST.
 *
 * <p>Quote lifecycle: {@code SENT → APPROVED | REJECTED}; any SENT or (unpaid) APPROVED quote becomes
 * {@code SUPERSEDED} when a revision is issued, so a revised quote always needs a fresh approval and an old
 * approval can never be paid.</p>
 */
@Service
public class RepairQuoteService {

    private final RepairQuoteRepository quotes;
    private final PaymentTransactionRepository payments;
    private final FeeCalculator fees;
    private final Clock clock;
    private final int validDays;

    public RepairQuoteService(RepairQuoteRepository quotes, PaymentTransactionRepository payments, FeeCalculator fees,
                              Clock clock, @Value("${reloop.quotes.valid-days:7}") int validDays) {
        this.quotes = quotes;
        this.payments = payments;
        this.fees = fees;
        this.clock = clock;
        this.validDays = validDays;
    }

    /**
     * Issues revision 1, or revision n+1 superseding the previous open quote. Fee rates are the configured ones
     * at this moment and are frozen on the quote.
     */
    @Transactional
    public RepairQuoteView create(CreateRepairQuoteRequest req) {
        RepairQuote previous = quotes.findFirstByRepairRequestExternalIdOrderByQuoteRevisionDesc(req.repairRequestId())
                .orElse(null);
        int revision = 1;
        if (previous != null) {
            if (!Objects.equals(previous.getCustomerAccountId(), req.customerAccountId())
                    || !Objects.equals(previous.getRepairerProfileId(), req.repairerProfileId())) {
                throw new ConflictException("Quote parties don't match the earlier quotes for this repair request");
            }
            if (isPaid(previous)) {
                throw new ConflictException("The current quote has already been paid and can't be revised");
            }
            if (previous.getStatus() == QuoteStatus.SENT || previous.getStatus() == QuoteStatus.APPROVED
                    || previous.getStatus() == QuoteStatus.DRAFT) {
                previous.setStatus(QuoteStatus.SUPERSEDED);
            }
            revision = previous.getQuoteRevision() + 1;
        }

        FeeCalculator.Breakdown b = fees.current(req.baseAmountPaise());
        LocalDateTime now = LocalDateTime.now(clock);
        RepairQuote q = new RepairQuote();
        q.setRepairRequestExternalId(req.repairRequestId());
        q.setRepairerProfileId(req.repairerProfileId());
        q.setCustomerAccountId(req.customerAccountId());
        q.setQuoteRevision(revision);
        q.setStatus(QuoteStatus.SENT);
        q.setSummary(blankToNull(req.summary()));
        q.setBaseAmountPaise(b.baseAmountPaise());
        q.setCustomerFeeRate(b.customerFeeRate());
        q.setCustomerFeePaise(b.customerFeePaise());
        q.setTotalPayablePaise(b.totalPayablePaise());
        q.setRepairerCommissionRate(b.repairerCommissionRate());
        q.setRepairerCommissionPaise(b.repairerCommissionPaise());
        q.setRepairerNetPaise(b.repairerNetPaise());
        q.setSentAt(now);
        q.setValidUntil(now.plusDays(validDays));
        // flush so the unique (request, revision) constraint fires inside this call on a concurrent duplicate
        return RepairQuoteView.of(quotes.saveAndFlush(q));
    }

    /** SENT → APPROVED (idempotent when already approved). Superseded, rejected or expired quotes → 409. */
    @Transactional
    public RepairQuoteView approve(Long quoteId, QuoteDecisionRequest req) {
        RepairQuote q = load(quoteId, req);
        if (q.getStatus() == QuoteStatus.APPROVED) {
            return RepairQuoteView.of(q);
        }
        requireOpen(q, "approved");
        if (q.getValidUntil() != null && LocalDateTime.now(clock).isAfter(q.getValidUntil())) {
            throw new ConflictException("Quote #" + q.getQuoteRevision() + " expired on " + q.getValidUntil().toLocalDate()
                    + "; ask the repairer for a revised quote", Map.of("quoteStatus", q.getStatus().name()));
        }
        q.setStatus(QuoteStatus.APPROVED);
        q.setApprovedAt(LocalDateTime.now(clock));
        return RepairQuoteView.of(q);
    }

    /** SENT → REJECTED (idempotent when already rejected). A rejected quote can never be paid. */
    @Transactional
    public RepairQuoteView reject(Long quoteId, QuoteDecisionRequest req) {
        RepairQuote q = load(quoteId, req);
        if (q.getStatus() == QuoteStatus.REJECTED) {
            return RepairQuoteView.of(q);
        }
        requireOpen(q, "rejected");
        q.setStatus(QuoteStatus.REJECTED);
        q.setRejectedAt(LocalDateTime.now(clock));
        return RepairQuoteView.of(q);
    }

    @Transactional(readOnly = true)
    public RepairQuoteView get(Long quoteId) {
        return RepairQuoteView.of(quotes.findById(quoteId)
                .orElseThrow(() -> new NotFoundException("Repair quote " + quoteId + " not found")));
    }

    private RepairQuote load(Long quoteId, QuoteDecisionRequest req) {
        RepairQuote q = quotes.findById(quoteId)
                .orElseThrow(() -> new NotFoundException("Repair quote " + quoteId + " not found"));
        if (!Objects.equals(q.getRepairRequestExternalId(), req.repairRequestId())
                || !Objects.equals(q.getCustomerAccountId(), req.customerAccountId())) {
            // don't reveal quotes of other requests/customers
            throw new NotFoundException("Repair quote " + quoteId + " not found");
        }
        return q;
    }

    private static void requireOpen(RepairQuote q, String verb) {
        if (q.getStatus() == QuoteStatus.SENT) {
            return;
        }
        String why = switch (q.getStatus()) {
            case SUPERSEDED -> "it was replaced by a revised quote";
            case REJECTED -> "it was rejected";
            case APPROVED -> "it was already approved";
            default -> "it is " + q.getStatus();
        };
        throw new ConflictException("Quote #" + q.getQuoteRevision() + " can't be " + verb + ": " + why,
                Map.of("quoteStatus", q.getStatus().name()));
    }

    private boolean isPaid(RepairQuote q) {
        return payments.findFirstByRepairQuoteIdAndStatus(q.getId(), PaymentStatus.SUCCESS).isPresent();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
