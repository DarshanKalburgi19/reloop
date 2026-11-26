package com.reloop.repair.service;

import com.reloop.repair.client.CreateQuoteCommand;
import com.reloop.repair.client.OrderServiceClient;
import com.reloop.repair.client.QuoteDecisionCommand;
import com.reloop.repair.client.QuoteView;
import com.reloop.repair.dto.repairer.PageResponse;
import com.reloop.repair.dto.repairrequest.CreateRepairRequest;
import com.reloop.repair.dto.repairrequest.HistoryEntryResponse;
import com.reloop.repair.dto.repairrequest.PaymentConfirmationRequest;
import com.reloop.repair.dto.repairrequest.PaymentEventRequest;
import com.reloop.repair.dto.repairrequest.QuoteRequest;
import com.reloop.repair.dto.repairrequest.RepairRequestResponse;
import com.reloop.repair.dto.repairrequest.RepairRequestSummaryResponse;
import com.reloop.repair.dto.repairrequest.StatusUpdateRequest;
import com.reloop.repair.entity.QuoteSnapshot;
import com.reloop.repair.entity.RepairPaymentEvent;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairService;
import com.reloop.repair.entity.RepairStatusHistory;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.PaymentEventType;
import com.reloop.repair.enums.RepairPaymentStatus;
import com.reloop.repair.enums.RepairServiceStatus;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.enums.RepairerProfileStatus;
import com.reloop.repair.enums.StatusChangeActor;
import com.reloop.repair.exception.OrderServiceException;
import com.reloop.repair.exception.ProfileNotFoundException;
import com.reloop.repair.exception.RepairRequestNotFoundException;
import com.reloop.repair.exception.RepairStateException;
import com.reloop.repair.exception.RepairerUnavailableException;
import com.reloop.repair.exception.RequestValidationException;
import com.reloop.repair.repository.RepairPaymentEventRepository;
import com.reloop.repair.repository.RepairRequestRepository;
import com.reloop.repair.repository.RepairStatusHistoryRepository;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The repair booking workflow. Owns repair requests, their status machine ({@link RepairTransitions},
 * {@link RepairAction}) and the append-only, timestamped history.
 *
 * <ul>
 *   <li>Customers see and act on their own requests only; repairers only on requests assigned to their
 *       workshop. Anyone else gets 404 (existence isn't revealed).</li>
 *   <li>Quotes and payments belong to order-service and are reached over REST ({@link OrderServiceClient}).
 *       The order-service call happens inside this transaction: if it fails, nothing changes here.</li>
 *   <li>Concurrent changes to one request are caught by optimistic locking (409).</li>
 * </ul>
 */
@Service
public class RepairRequestService {

    private static final int MAX_NUMBER_ATTEMPTS = 5;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final RepairRequestRepository requests;
    private final RepairStatusHistoryRepository history;
    private final RepairerProfileRepository profiles;
    private final OrderServiceClient orderService;
    private final RequestNumberGenerator numbers;
    private final RepairPaymentEventRepository paymentEvents;

    public RepairRequestService(RepairRequestRepository requests, RepairStatusHistoryRepository history,
                                RepairerProfileRepository profiles, OrderServiceClient orderService,
                                RequestNumberGenerator numbers, RepairPaymentEventRepository paymentEvents) {
        this.paymentEvents = paymentEvents;
        this.requests = requests;
        this.history = history;
        this.profiles = profiles;
        this.orderService = orderService;
        this.numbers = numbers;
    }

    /* ═════════════════════════════ customer ═════════════════════════════ */

    @Transactional
    public RepairRequestResponse create(AuthenticatedAccount caller, CreateRepairRequest req) {
        requireCustomer(caller);
        Map<String, String> errors = new LinkedHashMap<>();
        RepairerProfile profile = profiles.findById(req.repairerProfileId())
                .filter(p -> p.getStatus() == RepairerProfileStatus.ACTIVE && p.isProfileComplete())
                .orElse(null);
        if (profile == null) {
            throw new RequestValidationException(Map.of("repairerProfileId", "unknown workshop or not accepting requests"));
        }
        if (profile.getAvailability() == AvailabilityStatus.UNAVAILABLE) {
            throw new RepairerUnavailableException(profile.getBusinessName() + " is not accepting new repair requests"
                    + (profile.getAvailabilityNote() == null ? "" : ": " + profile.getAvailabilityNote()));
        }
        if (!profile.getCategories().contains(req.category())) {
            errors.put("category", "this workshop repairs " + RepairerMapper.categories(profile));
        }
        if (!RepairerMapper.repairMethods(profile).contains(req.deliveryMethod())) {
            errors.put("deliveryMethod", "this workshop offers " + RepairerMapper.repairMethods(profile));
        }
        RepairService service = null;
        if (req.repairServiceId() != null) {
            service = profile.getServices().stream()
                    .filter(s -> Objects.equals(s.getId(), req.repairServiceId()) && s.getStatus() == RepairServiceStatus.ACTIVE)
                    .findFirst().orElse(null);
            if (service == null) {
                errors.put("repairServiceId", "not an active service of this workshop");
            } else if (service.getCategory() != req.category()) {
                errors.put("repairServiceId", "this service is for " + service.getCategory() + ", not " + req.category());
            }
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }

        RepairRequest r = new RepairRequest();
        r.setRequestNumber(uniqueNumber());
        r.setCustomerAccountId(caller.accountId());
        r.setRepairerProfile(profile);
        r.setRepairService(service);
        r.setItemCategory(req.category());
        r.setItemDescription(req.itemDescription().trim());
        r.setProblemDescription(req.problemDescription().trim());
        if (req.imageUrls() != null) {
            req.imageUrls().stream().map(String::trim).distinct().forEach(r.getImageUrls()::add);
        }
        r.setDeliveryMode(RepairRequestMapper.toDeliveryMode(req.deliveryMethod()));
        r.setCustomerNotes(blankToNull(req.customerNotes()));
        r.setStatus(RepairStatus.REQUESTED);
        requests.saveAndFlush(r);
        record(r, null, RepairStatus.REQUESTED, StatusChangeActor.CUSTOMER, caller.accountId(),
                "Repair request submitted (" + req.deliveryMethod() + ")");
        return view(r, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<RepairRequestSummaryResponse> mine(AuthenticatedAccount caller, Collection<RepairStatus> statuses,
                                                           int page, int size) {
        requireCustomer(caller);
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<RepairRequest> result = statuses == null || statuses.isEmpty()
                ? requests.findByCustomerAccountId(caller.accountId(), pageable)
                : requests.findByCustomerAccountIdAndStatusIn(caller.accountId(), statuses, pageable);
        return PageResponse.of(result, RepairRequestMapper::toSummary);
    }

    @Transactional
    public RepairRequestResponse approveQuote(AuthenticatedAccount caller, Long id, String note) {
        RepairRequest r = forCustomer(caller, id);
        require(RepairAction.APPROVE_QUOTE, r);
        QuoteView quote = orderService.approveQuote(r.getLatestQuoteId(),
                new QuoteDecisionCommand(r.getId(), r.getCustomerAccountId(), blankToNull(note)));
        if (!Objects.equals(quote.id(), r.getLatestQuoteId()) || !"APPROVED".equals(quote.status())) {
            throw new OrderServiceException(502, "Order service did not confirm the quote approval", null);
        }
        move(r, RepairStatus.QUOTE_APPROVED, StatusChangeActor.CUSTOMER, caller.accountId(),
                withNote("Quote #" + quote.revision() + " approved (" + Money.format(quote.totalPayablePaise()) + ")", note));
        move(r, RepairStatus.AWAITING_PAYMENT, StatusChangeActor.SYSTEM, null,
                "Waiting for payment of " + Money.format(quote.totalPayablePaise()) + " through order-service");
        return save(r, false);
    }

    @Transactional
    public RepairRequestResponse rejectQuote(AuthenticatedAccount caller, Long id, String note) {
        RepairRequest r = forCustomer(caller, id);
        require(RepairAction.REJECT_QUOTE, r);
        QuoteView quote = orderService.rejectQuote(r.getLatestQuoteId(),
                new QuoteDecisionCommand(r.getId(), r.getCustomerAccountId(), blankToNull(note)));
        if (!Objects.equals(quote.id(), r.getLatestQuoteId()) || !"REJECTED".equals(quote.status())) {
            throw new OrderServiceException(502, "Order service did not confirm the quote rejection", null);
        }
        r.setApprovedAt(null);
        move(r, RepairStatus.QUOTE_REJECTED, StatusChangeActor.CUSTOMER, caller.accountId(),
                withNote("Quote #" + quote.revision() + " rejected", note));
        return save(r, false);
    }

    /* ═════════════════════════════ repairer ═════════════════════════════ */

    @Transactional(readOnly = true)
    public PageResponse<RepairRequestSummaryResponse> incoming(AuthenticatedAccount caller,
                                                               Collection<RepairStatus> statuses, int page, int size) {
        RepairerProfile profile = ownProfile(caller);
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<RepairRequest> result = statuses == null || statuses.isEmpty()
                ? requests.findByRepairerProfile_Id(profile.getId(), pageable)
                : requests.findByRepairerProfile_IdAndStatusIn(profile.getId(), statuses, pageable);
        return PageResponse.of(result, RepairRequestMapper::toSummary);
    }

    @Transactional
    public RepairRequestResponse accept(AuthenticatedAccount caller, Long id, String note) {
        RepairRequest r = forRepairer(caller, id);
        require(RepairAction.ACCEPT, r);
        move(r, RepairStatus.ACCEPTED, StatusChangeActor.REPAIRER, caller.accountId(),
                withNote("Request accepted", note));
        return save(r, true);
    }

    @Transactional
    public RepairRequestResponse reject(AuthenticatedAccount caller, Long id, String reason) {
        RepairRequest r = forRepairer(caller, id);
        require(RepairAction.REJECT, r);
        String why = requireNote(reason, "a reason is required when rejecting a request");
        r.setCloseReason(why);
        move(r, RepairStatus.REJECTED, StatusChangeActor.REPAIRER, caller.accountId(), "Request rejected: " + why);
        return save(r, true);
    }

    @Transactional
    public RepairRequestResponse inspect(AuthenticatedAccount caller, Long id, String findings) {
        RepairRequest r = forRepairer(caller, id);
        require(RepairAction.INSPECT, r);
        String text = requireNote(findings, "inspection findings are required");
        r.setInspectionNotes(text);
        move(r, RepairStatus.INSPECTED, StatusChangeActor.REPAIRER, caller.accountId(), "Item inspected: " + text);
        return save(r, true);
    }

    /**
     * {@code POST /api/repairs/{id}/quote}: first quote when the request is INSPECTED, otherwise a revision
     * (QUOTED, QUOTE_REJECTED, AWAITING_PAYMENT). Other states fail exactly like the quote endpoint.
     */
    @Transactional
    public RepairRequestResponse quoteOrRevise(AuthenticatedAccount caller, Long id, QuoteRequest req) {
        RepairRequest r = forRepairer(caller, id);
        boolean revise = RepairAction.REVISE_QUOTE.allowedIn(r.getStatus());
        return quote(caller, id, req, revise);
    }

    /**
     * Sends the first quote ({@code revise = false}, after inspection) or a revised one ({@code revise = true},
     * while unpaid). Either way the customer must approve the new quote; order-service supersedes the old one.
     */
    @Transactional
    public RepairRequestResponse quote(AuthenticatedAccount caller, Long id, QuoteRequest req, boolean revise) {
        RepairRequest r = forRepairer(caller, id);
        require(revise ? RepairAction.REVISE_QUOTE : RepairAction.QUOTE, r);
        long basePaise = Money.toPaise(req.amount());
        QuoteView quote = orderService.createQuote(new CreateQuoteCommand(r.getId(), r.getRequestNumber(),
                r.getRepairerProfile().getId(), r.getCustomerAccountId(), basePaise, req.summary().trim(),
                r.getRepairerProfile().getAccountExternalId()));
        if (!Objects.equals(quote.repairRequestId(), r.getId()) || quote.baseAmountPaise() != basePaise) {
            throw new OrderServiceException(502, "Order service returned a quote that doesn't match this request", null);
        }
        r.setLatestQuoteId(quote.id());
        r.setQuote(new QuoteSnapshot(quote.revision(), quote.baseAmountPaise(), quote.customerFeePaise(),
                quote.totalPayablePaise(), quote.repairerCommissionPaise(), quote.repairerNetPaise(),
                req.summary().trim(), req.estimatedDays(), quote.sentAt() != null ? quote.sentAt() : LocalDateTime.now()));
        r.setApprovedAt(null);
        String what = (revise ? "Revised quote #" : "Quote #") + quote.revision() + ": "
                + Money.format(quote.baseAmountPaise()) + " repair + " + Money.format(quote.customerFeePaise())
                + " platform fee = " + Money.format(quote.totalPayablePaise())
                + (revise ? ". Needs the customer's fresh approval" : "");
        move(r, RepairStatus.QUOTED, StatusChangeActor.REPAIRER, caller.accountId(), what);
        return save(r, true);
    }

    /** First call on a PAID job starts the repair (IN_PROGRESS); later calls add progress notes. */
    @Transactional
    public RepairRequestResponse progress(AuthenticatedAccount caller, Long id, String note) {
        RepairRequest r = forRepairer(caller, id);
        require(RepairAction.PROGRESS, r);
        if (r.getStatus() == RepairStatus.PAID) {
            move(r, RepairStatus.IN_PROGRESS, StatusChangeActor.REPAIRER, caller.accountId(),
                    withNote("Repair started", note));
        } else {
            String text = requireNote(note, "describe the progress update");
            move(r, RepairStatus.IN_PROGRESS, StatusChangeActor.REPAIRER, caller.accountId(), "Progress: " + text);
        }
        return save(r, true);
    }

    @Transactional
    public RepairRequestResponse complete(AuthenticatedAccount caller, Long id, String note) {
        RepairRequest r = forRepairer(caller, id);
        require(RepairAction.COMPLETE, r);
        move(r, RepairStatus.COMPLETED, StatusChangeActor.REPAIRER, caller.accountId(),
                withNote("Repair completed", note));
        return save(r, true);
    }

    /** {@code PATCH /status}: the repairer actions in generic form. */
    @Transactional
    public RepairRequestResponse updateStatus(AuthenticatedAccount caller, Long id, StatusUpdateRequest req) {
        return switch (req.status()) {
            case ACCEPTED -> accept(caller, id, req.note());
            case REJECTED -> reject(caller, id, req.note());
            case INSPECTED -> inspect(caller, id, req.note());
            case IN_PROGRESS -> progress(caller, id, req.note());
            case COMPLETED -> complete(caller, id, req.note());
            case CANCELLED -> cancel(caller, id, req.note());
            default -> throw new RequestValidationException(Map.of("status",
                    "repairers can set ACCEPTED, REJECTED, INSPECTED, IN_PROGRESS, COMPLETED or CANCELLED; "
                            + "quotes use POST /quote, quote decisions and payments belong to the customer"));
        };
    }

    /* ═════════════════════════════ shared ═════════════════════════════ */

    @Transactional(readOnly = true)
    public RepairRequestResponse get(AuthenticatedAccount caller, Long id) {
        RepairRequest r = forParticipant(caller, id);
        return view(r, caller.isRepairer());
    }

    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> history(AuthenticatedAccount caller, Long id) {
        RepairRequest r = forParticipant(caller, id);
        return history.findByRepairRequest_IdOrderByCreatedAtAscIdAsc(r.getId()).stream()
                .map(RepairRequestMapper::toHistory).toList();
    }

    /** Customer (any time before payment) or assigned repairer (after accepting, before payment). */
    @Transactional
    public RepairRequestResponse cancel(AuthenticatedAccount caller, Long id, String reason) {
        RepairRequest r = forParticipant(caller, id);
        boolean repairer = caller.isRepairer();
        require(repairer ? RepairAction.REPAIRER_CANCEL : RepairAction.CUSTOMER_CANCEL, r);
        String why = requireNote(reason, "a reason is required when cancelling");
        r.setCloseReason(why);
        move(r, RepairStatus.CANCELLED, repairer ? StatusChangeActor.REPAIRER : StatusChangeActor.CUSTOMER,
                caller.accountId(), "Cancelled by " + (repairer ? "repairer" : "customer") + ": " + why);
        return save(r, repairer);
    }

    /* ═════════════════════════════ order-service callback ═════════════════════════════ */

    /**
     * order-service reports a successful payment. Accepted only for the CURRENT, approved quote while the
     * request is AWAITING_PAYMENT and for the exact amount.
     *
     * <p>Idempotent per quote: order-service settles at most one payment per quote (row lock + unique settlement
     * key), so a second confirmation for the quote that was already paid is a retry, never a second payment. That
     * includes the case where order-service timed out waiting for this call, rolled its own transaction back and the
     * customer paid again with a new transaction id: the newer reference is adopted instead of refusing it with 409,
     * which would otherwise make the quote unpayable forever.</p>
     */
    @Transactional
    public RepairRequestResponse confirmPayment(Long id, PaymentConfirmationRequest req) {
        RepairRequest r = requests.findById(id).orElseThrow(() -> new RepairRequestNotFoundException(id));
        if (PAID_OR_LATER.contains(r.getStatus()) && Objects.equals(r.getLatestQuoteId(), req.quoteId())) {
            requireApprovedTotal(r, req.amountPaise());
            if (Objects.equals(r.getPaymentTransactionId(), req.paymentTransactionId())
                    && Objects.equals(r.getPaymentReference(), req.transactionId())) {
                return view(r, false); // duplicate delivery of the same confirmation
            }
            String previous = r.getPaymentReference() != null ? r.getPaymentReference() : "#" + r.getPaymentTransactionId();
            r.setPaymentTransactionId(req.paymentTransactionId());
            r.setPaymentReference(req.transactionId());
            record(r, r.getStatus(), r.getStatus(), StatusChangeActor.SYSTEM, null, "Payment reference updated from "
                    + previous + " to " + req.transactionId() + " (order-service retried the confirmation)");
            return save(r, false);
        }
        require(RepairAction.PAY, r);
        if (!Objects.equals(r.getLatestQuoteId(), req.quoteId())) {
            throw new RepairStateException(r.getStatus(), "Payment is for quote " + req.quoteId()
                    + " but the current quote is " + r.getLatestQuoteId() + "; the revised quote needs approval first");
        }
        requireApprovedTotal(r, req.amountPaise());
        r.setPaymentTransactionId(req.paymentTransactionId());
        r.setPaymentReference(req.transactionId());
        r.setPaymentStatus(RepairPaymentStatus.PAID);
        r.setLastPaymentFailure(null);
        move(r, RepairStatus.PAID, StatusChangeActor.SYSTEM, null,
                "Payment " + req.transactionId() + " received: " + Money.format(req.amountPaise()));
        return save(r, false);
    }

    /**
     * order-service reports a payment event other than success: a failed / cancelled attempt (nothing charged) or
     * a refund. Idempotent per {@code eventId}. Effects:
     * <ul>
     *   <li>FAILED / CANCELLED: only recorded while the request is still AWAITING_PAYMENT (a late failure of an
     *       older attempt never overrides a payment that succeeded); the workflow status does not change.</li>
     *   <li>PARTIALLY_REFUNDED: payment status and refunded total are updated; the job continues.</li>
     *   <li>REFUNDED: if the work isn't finished (PAID / IN_PROGRESS) the request is closed as CANCELLED by the
     *       system (the only way a paid request can be closed); a COMPLETED request stays COMPLETED.</li>
     * </ul>
     * Refunds must name the payment this request was paid with (409 otherwise).
     */
    @Transactional
    public RepairRequestResponse recordPaymentEvent(Long id, PaymentEventRequest req) {
        RepairRequest r = requests.findById(id).orElseThrow(() -> new RepairRequestNotFoundException(id));
        if (paymentEvents.existsByEventId(req.eventId())) {
            return view(r, false); // duplicate delivery
        }
        RepairStatus status = r.getStatus();
        switch (req.type()) {
            case FAILED, CANCELLED -> {
                if (status == RepairStatus.AWAITING_PAYMENT) {
                    String why = req.reason() == null || req.reason().isBlank()
                            ? (req.type() == PaymentEventType.FAILED ? "Payment declined" : "Payment cancelled")
                            : req.reason().strip();
                    r.setPaymentStatus(req.type() == PaymentEventType.FAILED
                            ? RepairPaymentStatus.FAILED : RepairPaymentStatus.CANCELLED);
                    r.setLastPaymentFailure(truncate(why, 500));
                    record(r, status, status, StatusChangeActor.SYSTEM, null, "Payment " + req.transactionId() + " "
                            + req.type().name().toLowerCase(java.util.Locale.ROOT) + " (" + Money.format(req.amountPaise())
                            + "): " + why + ". Nothing was charged; the quote can still be paid");
                }
            }
            case PARTIALLY_REFUNDED, REFUNDED -> {
                if (!req.transactionId().equals(r.getPaymentReference())) {
                    throw new RepairStateException(status, "Refund is for payment " + req.transactionId()
                            + " but this repair request was paid with " + (r.getPaymentReference() == null
                            ? "no recorded payment" : r.getPaymentReference()));
                }
                boolean full = req.type() == PaymentEventType.REFUNDED;
                r.setRefundedAmountPaise(Math.max(r.getRefundedAmountPaise(), req.refundedAmountPaise()));
                if (r.getPaymentStatus() != RepairPaymentStatus.REFUNDED) {
                    r.setPaymentStatus(full ? RepairPaymentStatus.REFUNDED : RepairPaymentStatus.PARTIALLY_REFUNDED);
                }
                String note = (full ? "Payment " + req.transactionId() + " fully refunded"
                        : "Payment " + req.transactionId() + " partly refunded") + ": "
                        + Money.format(r.getRefundedAmountPaise()) + " returned to the customer in total"
                        + (req.reason() == null || req.reason().isBlank() ? "" : " (" + req.reason().strip() + ")");
                if (full && RepairTransitions.closableByRefund(status)) {
                    r.setCloseReason(truncate("Payment refunded (" + req.transactionId() + ")", 500));
                    r.setClosedAt(LocalDateTime.now());
                    r.setStatus(RepairStatus.CANCELLED);
                    record(r, status, RepairStatus.CANCELLED, StatusChangeActor.SYSTEM, null,
                            note + "; the unfinished repair is closed");
                } else {
                    record(r, status, status, StatusChangeActor.SYSTEM, null, note);
                }
            }
            default -> throw new IllegalStateException("unhandled payment event " + req.type());
        }
        paymentEvents.save(new RepairPaymentEvent(req.eventId(), r.getId(), req.type(), req.transactionId(),
                req.amountPaise(), req.refundedAmountPaise()));
        return save(r, false);
    }

    private static final java.util.Set<RepairStatus> PAID_OR_LATER =
            java.util.EnumSet.of(RepairStatus.PAID, RepairStatus.IN_PROGRESS, RepairStatus.COMPLETED);

    private static void requireApprovedTotal(RepairRequest r, long amountPaise) {
        long expected = r.getQuote().getTotalPayablePaise();
        if (amountPaise != expected) {
            throw new RepairStateException(r.getStatus(), "Payment amount " + amountPaise
                    + " paise doesn't match the approved total of " + expected + " paise");
        }
    }

    /* ═════════════════════════════ helpers ═════════════════════════════ */

    private void move(RepairRequest r, RepairStatus to, StatusChangeActor actor, Long accountId, String note) {
        RepairStatus from = r.getStatus();
        if (!RepairTransitions.allowed(from, to)) {
            throw new RepairStateException(from, "A repair request can't move from " + from + " to " + to);
        }
        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case ACCEPTED -> r.setAcceptedAt(now);
            case INSPECTED -> r.setInspectedAt(now);
            case QUOTED -> r.setQuotedAt(now);
            case QUOTE_APPROVED -> r.setApprovedAt(now);
            case PAID -> r.setPaidAt(now);
            case IN_PROGRESS -> {
                if (r.getInProgressAt() == null) {
                    r.setInProgressAt(now);
                }
            }
            case COMPLETED -> r.setCompletedAt(now);
            case REJECTED, CANCELLED -> r.setClosedAt(now);
            default -> {
            }
        }
        r.setStatus(to);
        record(r, from, to, actor, accountId, note);
    }

    private void record(RepairRequest r, RepairStatus from, RepairStatus to, StatusChangeActor actor, Long accountId,
                        String note) {
        RepairStatusHistory entry = new RepairStatusHistory();
        entry.setRepairRequest(r);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(actor);
        entry.setChangedByAccountId(accountId);
        entry.setNote(note == null ? null : truncate(note, 1000));
        history.save(entry);
    }

    private RepairRequestResponse save(RepairRequest r, boolean viewerIsRepairer) {
        requests.saveAndFlush(r); // flush now so an optimistic-lock conflict surfaces as 409 here
        return view(r, viewerIsRepairer);
    }

    private RepairRequestResponse view(RepairRequest r, boolean viewerIsRepairer) {
        history.flush();
        return RepairRequestMapper.toResponse(r, viewerIsRepairer,
                history.findByRepairRequest_IdOrderByCreatedAtAscIdAsc(r.getId()));
    }

    private static void require(RepairAction action, RepairRequest r) {
        if (!action.allowedIn(r.getStatus())) {
            throw new RepairStateException(r.getStatus(), action.refusal(r.getStatus()));
        }
    }

    private RepairRequest forCustomer(AuthenticatedAccount caller, Long id) {
        requireCustomer(caller);
        RepairRequest r = requests.findById(id).orElseThrow(() -> new RepairRequestNotFoundException(id));
        if (!Objects.equals(r.getCustomerAccountId(), caller.accountId())) {
            throw new RepairRequestNotFoundException(id);
        }
        return r;
    }

    private RepairRequest forRepairer(AuthenticatedAccount caller, Long id) {
        if (caller == null || !caller.isRepairer()) {
            throw new AccessDeniedException("Only the assigned repairer can do this");
        }
        RepairRequest r = requests.findById(id).orElseThrow(() -> new RepairRequestNotFoundException(id));
        RepairerProfile own = profiles.findByAccountExternalId(caller.accountId())
                .orElseThrow(() -> new RepairRequestNotFoundException(id));
        if (!Objects.equals(r.getRepairerProfile().getId(), own.getId())) {
            throw new RepairRequestNotFoundException(id);
        }
        return r;
    }

    private RepairRequest forParticipant(AuthenticatedAccount caller, Long id) {
        if (caller == null) {
            throw new AccessDeniedException("Log in to view repair requests");
        }
        return caller.isRepairer() ? forRepairer(caller, id) : forCustomer(caller, id);
    }

    private RepairerProfile ownProfile(AuthenticatedAccount caller) {
        if (caller == null || !caller.isRepairer()) {
            throw new AccessDeniedException("Only repairers have an incoming request list");
        }
        return profiles.findByAccountExternalId(caller.accountId()).orElseThrow(ProfileNotFoundException::noOwnProfile);
    }

    private static void requireCustomer(AuthenticatedAccount caller) {
        if (caller == null || !AuthenticatedAccount.CUSTOMER.equals(caller.role())) {
            throw new AccessDeniedException("Only customer accounts can do this");
        }
    }

    private String uniqueNumber() {
        for (int i = 0; i < MAX_NUMBER_ATTEMPTS; i++) {
            String candidate = numbers.next();
            if (!requests.existsByRequestNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique repair request number");
    }

    private static String requireNote(String note, String message) {
        if (note == null || note.isBlank()) {
            throw new RequestValidationException(Map.of("note", message));
        }
        return note.trim();
    }

    private static String withNote(String base, String note) {
        return note == null || note.isBlank() ? base : base + ": " + note.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
