package com.reloop.repair.service;

import com.reloop.repair.dto.repairer.LatLng;
import com.reloop.repair.dto.repairrequest.HistoryEntryResponse;
import com.reloop.repair.dto.repairrequest.QuoteResponse;
import com.reloop.repair.dto.repairrequest.RepairRequestResponse;
import com.reloop.repair.dto.repairrequest.RepairRequestSummaryResponse;
import com.reloop.repair.dto.repairrequest.RepairerCard;
import com.reloop.repair.entity.QuoteSnapshot;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairStatusHistory;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.enums.DeliveryMode;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairStatus;
import java.math.BigDecimal;
import java.util.List;

/** Entity -> API mapping for repair requests. Call inside a transaction (lazy associations). */
public final class RepairRequestMapper {

    private RepairRequestMapper() {
    }

    public static DeliveryMode toDeliveryMode(RepairMethod method) {
        return switch (method) {
            case IN_PERSON -> DeliveryMode.IN_PERSON;
            case SEND_ITEM -> DeliveryMode.SHIPPING;
            case PICKUP_DROPOFF -> DeliveryMode.PICKUP_DROP;
        };
    }

    public static RepairMethod toRepairMethod(DeliveryMode mode) {
        return switch (mode) {
            case IN_PERSON -> RepairMethod.IN_PERSON;
            case SHIPPING -> RepairMethod.SEND_ITEM;
            case PICKUP_DROP -> RepairMethod.PICKUP_DROPOFF;
        };
    }

    public static RepairRequestResponse toResponse(RepairRequest r, boolean viewerIsRepairer,
                                                   List<RepairStatusHistory> history) {
        RepairerProfile p = r.getRepairerProfile();
        return new RepairRequestResponse(
                r.getId(), r.getRequestNumber(), r.getStatus(), r.getCustomerAccountId(),
                new RepairerCard(p.getId(), p.getBusinessName(), p.getPhone(), p.getAddressLine(), p.getCity(),
                        p.getPincode(), p.getLatitude() == null || p.getLongitude() == null ? null
                                : new LatLng(p.getLatitude(), p.getLongitude())),
                r.getRepairService() == null ? null : r.getRepairService().getId(),
                r.getRepairService() == null ? null : r.getRepairService().getName(),
                r.getItemCategory(), r.getItemDescription(), r.getProblemDescription(), List.copyOf(r.getImageUrls()),
                toRepairMethod(r.getDeliveryMode()), deliveryInstructions(r),
                r.getCustomerNotes(), r.getInspectionNotes(), toQuote(r), r.getPaymentTransactionId(), r.getPaymentReference(),
                r.getPaymentStatus(), r.getRefundedAmountPaise(), r.getLastPaymentFailure(), r.getCloseReason(),
                RepairAction.available(r.getStatus(), viewerIsRepairer),
                r.getCreatedAt(), r.getUpdatedAt(), r.getAcceptedAt(), r.getInspectedAt(), r.getQuotedAt(),
                r.getApprovedAt(), r.getPaidAt(), r.getInProgressAt(), r.getCompletedAt(), r.getClosedAt(),
                history.stream().map(RepairRequestMapper::toHistory).toList());
    }

    public static RepairRequestSummaryResponse toSummary(RepairRequest r) {
        QuoteSnapshot q = r.getQuote();
        return new RepairRequestSummaryResponse(r.getId(), r.getRequestNumber(), r.getStatus(), r.getCustomerAccountId(),
                r.getRepairerProfile().getId(), r.getRepairerProfile().getBusinessName(), r.getItemCategory(),
                r.getItemDescription(), toRepairMethod(r.getDeliveryMode()),
                q == null || q.getTotalPayablePaise() == null ? null : Money.toRupees(q.getTotalPayablePaise()),
                r.getCreatedAt(), r.getUpdatedAt(), r.getPaymentStatus(), r.getPaymentReference());
    }

    public static HistoryEntryResponse toHistory(RepairStatusHistory h) {
        return new HistoryEntryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(), h.getNote(), h.getCreatedAt());
    }

    static QuoteResponse toQuote(RepairRequest r) {
        QuoteSnapshot q = r.getQuote();
        if (r.getLatestQuoteId() == null || q == null || q.getRevision() == null) {
            return null;
        }
        return new QuoteResponse(r.getLatestQuoteId(), q.getRevision(), quoteState(r.getStatus()),
                rupees(q.getBaseAmountPaise()), rupees(q.getCustomerFeePaise()), rupees(q.getTotalPayablePaise()),
                q.getTotalPayablePaise() == null ? null : Money.format(q.getTotalPayablePaise()),
                rupees(q.getRepairerCommissionPaise()), rupees(q.getRepairerNetPaise()),
                q.getSummary(), q.getEstimatedDays(), q.getSentAt());
    }

    static String quoteState(RepairStatus status) {
        return switch (status) {
            case QUOTED -> "AWAITING_APPROVAL";
            case QUOTE_REJECTED -> "REJECTED";
            case QUOTE_APPROVED, AWAITING_PAYMENT -> "APPROVED";
            case PAID, IN_PROGRESS, COMPLETED -> "PAID";
            case CANCELLED -> "WITHDRAWN";
            default -> "AWAITING_APPROVAL";
        };
    }

    /** Where the item goes, based on the delivery method and the workshop's address. */
    static String deliveryInstructions(RepairRequest r) {
        RepairerProfile p = r.getRepairerProfile();
        String address = p.getBusinessName() + ", " + p.getAddressLine() + ", " + p.getCity()
                + (p.getPincode() == null ? "" : " " + p.getPincode());
        return switch (r.getDeliveryMode()) {
            case IN_PERSON -> "Bring the item to " + address
                    + (p.getServiceHours() == null ? "" : " (open " + p.getServiceHours() + ")")
                    + " once the repairer has accepted the request.";
            case SHIPPING -> "After the repairer accepts, courier the item to " + address
                    + ". Write " + r.getRequestNumber() + " on the parcel.";
            case PICKUP_DROP -> "After accepting, " + p.getBusinessName()
                    + " will contact you to arrange pickup and drop-off of the item.";
        };
    }

    private static BigDecimal rupees(Long paise) {
        return paise == null ? null : Money.toRupees(paise);
    }
}
