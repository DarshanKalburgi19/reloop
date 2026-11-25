package com.reloop.repair.service;

import com.reloop.repair.dto.inventory.AdjustStockRequest;
import com.reloop.repair.dto.inventory.CreateSparePartRequest;
import com.reloop.repair.dto.inventory.InventoryMovementResponse;
import com.reloop.repair.dto.inventory.InventorySummaryResponse;
import com.reloop.repair.dto.inventory.InventorySummaryResponse.CategorySummary;
import com.reloop.repair.dto.inventory.LowStockResponse;
import com.reloop.repair.dto.inventory.RepairPartsUsageResponse;
import com.reloop.repair.dto.inventory.SparePartResponse;
import com.reloop.repair.dto.inventory.StockChangeResponse;
import com.reloop.repair.dto.inventory.StockInRequest;
import com.reloop.repair.dto.inventory.UpdateSparePartRequest;
import com.reloop.repair.dto.inventory.UsePartRequest;
import com.reloop.repair.dto.repairer.PageResponse;
import com.reloop.repair.entity.InventoryMovement;
import com.reloop.repair.entity.RepairRequest;
import com.reloop.repair.entity.RepairerProfile;
import com.reloop.repair.entity.SparePart;
import com.reloop.repair.enums.InventoryMovementType;
import com.reloop.repair.enums.RepairStatus;
import com.reloop.repair.enums.SparePartCategory;
import com.reloop.repair.enums.SparePartStatus;
import com.reloop.repair.exception.InsufficientStockException;
import com.reloop.repair.exception.InventoryConflictException;
import com.reloop.repair.exception.ProfileNotFoundException;
import com.reloop.repair.exception.RepairRequestNotFoundException;
import com.reloop.repair.exception.RepairStateException;
import com.reloop.repair.exception.RequestValidationException;
import com.reloop.repair.exception.SparePartNotFoundException;
import com.reloop.repair.repository.InventoryMovementRepository;
import com.reloop.repair.repository.RepairRequestRepository;
import com.reloop.repair.repository.RepairerProfileRepository;
import com.reloop.repair.repository.SparePartRepository;
import com.reloop.repair.security.AuthenticatedAccount;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spare-parts inventory of the calling repairer.
 *
 * <ul>
 *   <li><b>Owner only.</b> Every lookup is scoped to the caller's own workshop profile; another repairer's part or
 *       repair request is reported as 404, a non-repairer as 403.</li>
 *   <li><b>Never negative.</b> Stock-changing methods lock the part row ({@code SELECT … FOR UPDATE}) and refuse a
 *       change that would take the quantity below 0 ({@link InsufficientStockException}, 409). The database CHECK
 *       constraint is the last line of defence.</li>
 *   <li><b>Every change is recorded.</b> The quantity changes only through {@link #record}, which writes an
 *       {@link InventoryMovement} with the signed change, balance after and unit cost. Info edits don't touch the
 *       quantity.</li>
 *   <li><b>Repairs consume stock.</b> {@link #use} decrements the part and links the movement to a repair request
 *       assigned to the caller that is {@code PAID} or {@code IN_PROGRESS} (work may only start after payment).</li>
 *   <li><b>Valuation is separate.</b> Inventory value = Σ quantity × unit cost, computed from inventory data only;
 *       it never touches quotes, payments, commissions or payouts.</li>
 * </ul>
 */
@Service
@Transactional
public class InventoryService {

    /** Repair statuses in which parts may be recorded as used. */
    public static final Set<RepairStatus> USAGE_STATUSES = EnumSet.of(RepairStatus.PAID, RepairStatus.IN_PROGRESS);

    public static final String VALUATION_NOTE = "Inventory value is the purchase cost of the parts you have on hand"
            + " (quantity x unit cost). It is tracked separately and is not included in your repair earnings.";

    private static final int MAX_SKU_ATTEMPTS = 10;
    private static final Map<String, Sort> PART_SORTS = Map.of(
            "name", Sort.by(Sort.Order.asc("partName"), Sort.Order.asc("id")),
            "quantity", Sort.by(Sort.Order.asc("quantity"), Sort.Order.asc("partName")),
            "newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            "updated", Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    private static final Sort MOVEMENT_SORT = Sort.by(Sort.Order.desc("movementDate"), Sort.Order.desc("id"));

    private final SparePartRepository parts;
    private final InventoryMovementRepository movements;
    private final RepairerProfileRepository profiles;
    private final RepairRequestRepository requests;
    private final SkuGenerator skus;

    public InventoryService(SparePartRepository parts, InventoryMovementRepository movements,
                            RepairerProfileRepository profiles, RepairRequestRepository requests, SkuGenerator skus) {
        this.parts = parts;
        this.movements = movements;
        this.profiles = profiles;
        this.requests = requests;
        this.skus = skus;
    }

    /* ─────────────────────────────── parts ─────────────────────────────── */

    public SparePartResponse create(AuthenticatedAccount caller, CreateSparePartRequest req) {
        RepairerProfile profile = ownProfile(caller);
        String partName = requireText(req.partName(), "partName", "partName is required");
        String sku = req.sku() == null ? uniqueSku(profile.getId(), req.category()) : normaliseSku(req.sku());
        if (req.sku() != null && parts.existsByRepairerProfile_IdAndSku(profile.getId(), sku)) {
            throw new InventoryConflictException("You already have a part with SKU " + sku);
        }
        long unitCostPaise = Money.toPaise(req.unitCost());

        SparePart p = new SparePart();
        p.setRepairerProfile(profile);
        p.setSku(sku);
        p.setPartName(partName);
        p.setCategory(req.category());
        p.setDescription(blankToNull(req.description()));
        p.setUnitCostPaise(unitCostPaise);
        p.setLowStockThreshold(req.lowStockThreshold() == null ? 5 : req.lowStockThreshold());
        p.setQuantity(0);
        p.setStatus(SparePartStatus.ACTIVE);
        parts.save(p);

        int opening = req.quantity() == null ? 0 : req.quantity();
        if (opening > 0) {
            record(p, InventoryMovementType.STOCK_IN, opening, unitCostPaise, null, "Opening stock", null);
        }
        return InventoryMapper.toPart(p);
    }

    public SparePartResponse update(AuthenticatedAccount caller, Long id, UpdateSparePartRequest req) {
        if (req == null || req.isEmpty()) {
            throw new RequestValidationException(Map.of("body", "send at least one field to change"));
        }
        RepairerProfile profile = ownProfile(caller);
        // locked so an info edit can't write back a stale quantity over a concurrent stock movement
        SparePart p = lockedPart(profile, id);
        if (req.partName() != null) {
            p.setPartName(requireText(req.partName(), "partName", "partName can't be blank"));
        }
        if (req.category() != null) {
            p.setCategory(req.category());
        }
        if (req.sku() != null) {
            String sku = normaliseSku(req.sku());
            if (parts.existsByRepairerProfile_IdAndSkuAndIdNot(profile.getId(), sku, p.getId())) {
                throw new InventoryConflictException("You already have a part with SKU " + sku);
            }
            p.setSku(sku);
        }
        if (req.description() != null) {
            p.setDescription(blankToNull(req.description()));
        }
        if (req.unitCost() != null) {
            p.setUnitCostPaise(Money.toPaise(req.unitCost()));
        }
        if (req.lowStockThreshold() != null) {
            p.setLowStockThreshold(req.lowStockThreshold());
        }
        return InventoryMapper.toPart(parts.saveAndFlush(p));
    }

    @Transactional(readOnly = true)
    public SparePartResponse get(AuthenticatedAccount caller, Long id) {
        return InventoryMapper.toPart(ownedPart(ownProfile(caller), id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SparePartResponse> list(AuthenticatedAccount caller, SparePartCategory category, String q,
                                                boolean lowStockOnly, boolean includeDiscontinued, String sort,
                                                int page, int size) {
        Long profileId = ownProfile(caller).getId();
        Sort order = PART_SORTS.get(sort == null ? "name" : sort.toLowerCase(Locale.ROOT));
        if (order == null) {
            throw new RequestValidationException(Map.of("sort", "use one of " + String.join(", ", PART_SORTS.keySet())));
        }
        String needle = q == null || q.isBlank() ? null : "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        Specification<SparePart> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("repairerProfile").get("id"), profileId));
            if (!includeDiscontinued) {
                where.add(cb.equal(root.get("status"), SparePartStatus.ACTIVE));
            }
            if (category != null) {
                where.add(cb.equal(root.get("category"), category));
            }
            if (lowStockOnly) {
                where.add(cb.le(root.<Integer>get("quantity"), root.<Integer>get("lowStockThreshold")));
            }
            if (needle != null) {
                where.add(cb.or(cb.like(cb.lower(root.<String>get("partName")), needle, '\\'),
                        cb.like(cb.lower(root.<String>get("sku")), needle, '\\')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.of(parts.findAll(spec, PageRequest.of(page, size, order)), InventoryMapper::toPart);
    }

    /** Hides a part from the active list and stops new stock-in. History and remaining units are kept. */
    public SparePartResponse discontinue(AuthenticatedAccount caller, Long id) {
        SparePart p = lockedPart(ownProfile(caller), id);
        p.setStatus(SparePartStatus.DISCONTINUED);
        return InventoryMapper.toPart(parts.saveAndFlush(p));
    }

    public SparePartResponse reactivate(AuthenticatedAccount caller, Long id) {
        SparePart p = lockedPart(ownProfile(caller), id);
        p.setStatus(SparePartStatus.ACTIVE);
        return InventoryMapper.toPart(parts.saveAndFlush(p));
    }

    /* ─────────────────────────────── stock movements ─────────────────────────────── */

    /** STOCK_IN: stock received. A delivery price updates the unit cost to the weighted average. */
    public StockChangeResponse stockIn(AuthenticatedAccount caller, Long id, StockInRequest req) {
        SparePart p = lockedPart(ownProfile(caller), id);
        if (p.getStatus() != SparePartStatus.ACTIVE) {
            throw new InventoryConflictException("'" + p.getPartName() + "' is discontinued. Reactivate it before receiving stock.");
        }
        long currentCost = p.getUnitCostPaise() == null ? 0L : p.getUnitCostPaise();
        long deliveryCost = req.unitCost() == null ? currentCost : Money.toPaise(req.unitCost());
        long averaged = weightedAverage(p.getQuantity(), currentCost, req.quantity(), deliveryCost);

        InventoryMovement m = record(p, InventoryMovementType.STOCK_IN, req.quantity(), deliveryCost, null,
                blankToNull(req.note()), req.movementDate());
        p.setUnitCostPaise(averaged);
        return changed(p, m);
    }

    /** USED_IN_REPAIR: parts fitted during one of the caller's repairs (PAID or IN_PROGRESS). */
    public StockChangeResponse use(AuthenticatedAccount caller, Long id, UsePartRequest req) {
        RepairerProfile profile = ownProfile(caller);
        SparePart p = lockedPart(profile, id);
        RepairRequest repair = ownRepair(profile, req.repairRequestId());
        if (!USAGE_STATUSES.contains(repair.getStatus())) {
            throw new RepairStateException(repair.getStatus(), "Parts can only be recorded for a repair that is PAID or"
                    + " IN_PROGRESS; " + repair.getRequestNumber() + " is " + repair.getStatus());
        }
        long cost = p.getUnitCostPaise() == null ? 0L : p.getUnitCostPaise();
        InventoryMovement m = record(p, InventoryMovementType.USED_IN_REPAIR, -req.quantity(), cost, repair,
                blankToNull(req.note()), null);
        return changed(p, m);
    }

    /** ADJUSTMENT: set the counted quantity or apply a signed correction, with a mandatory reason. */
    public StockChangeResponse adjust(AuthenticatedAccount caller, Long id, AdjustStockRequest req) {
        if ((req.newQuantity() == null) == (req.quantityChange() == null)) {
            throw new RequestValidationException(Map.of("newQuantity", "send exactly one of newQuantity or quantityChange"));
        }
        RepairerProfile profile = ownProfile(caller);
        SparePart p = lockedPart(profile, id);
        int delta = req.newQuantity() != null ? req.newQuantity() - p.getQuantity() : req.quantityChange();
        if (delta == 0) {
            String field = req.newQuantity() != null ? "newQuantity" : "quantityChange";
            throw new RequestValidationException(Map.of(field, "no change: the quantity is already " + p.getQuantity()));
        }
        RepairRequest repair = req.repairRequestId() == null ? null : ownRepair(profile, req.repairRequestId());
        String reason = requireText(req.reason(), "reason", "reason is required for a stock adjustment");
        long cost = p.getUnitCostPaise() == null ? 0L : p.getUnitCostPaise();
        InventoryMovement m = record(p, InventoryMovementType.ADJUSTMENT, delta, cost, repair, reason, null);
        return changed(p, m);
    }

    /* ─────────────────────────────── reports ─────────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> partMovements(AuthenticatedAccount caller, Long id, int page, int size) {
        SparePart p = ownedPart(ownProfile(caller), id);
        return PageResponse.of(movements.findBySparePart_Id(p.getId(), PageRequest.of(page, size, MOVEMENT_SORT)),
                InventoryMapper::toMovement);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> movements(AuthenticatedAccount caller, InventoryMovementType type,
                                                             Long sparePartId, Long repairRequestId, LocalDate from,
                                                             LocalDate to, int page, int size) {
        Long profileId = ownProfile(caller).getId();
        if (from != null && to != null && from.isAfter(to)) {
            throw new RequestValidationException(Map.of("from", "from must be on or before to"));
        }
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime endExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        Specification<InventoryMovement> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("repairerProfile").get("id"), profileId));
            if (type != null) {
                where.add(cb.equal(root.get("movementType"), type));
            }
            if (sparePartId != null) {
                where.add(cb.equal(root.get("sparePart").get("id"), sparePartId));
            }
            if (repairRequestId != null) {
                where.add(cb.equal(root.get("repairRequest").get("id"), repairRequestId));
            }
            if (start != null) {
                where.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("movementDate"), start));
            }
            if (endExclusive != null) {
                where.add(cb.lessThan(root.<LocalDateTime>get("movementDate"), endExclusive));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.of(movements.findAll(spec, PageRequest.of(page, size, MOVEMENT_SORT)),
                InventoryMapper::toMovement);
    }

    @Transactional(readOnly = true)
    public LowStockResponse lowStock(AuthenticatedAccount caller) {
        List<SparePart> low = parts.findLowStock(ownProfile(caller).getId(), SparePartStatus.ACTIVE);
        int out = (int) low.stream().filter(p -> p.getQuantity() == 0).count();
        String message = low.isEmpty() ? null
                : low.size() + " part" + (low.size() == 1 ? " is" : "s are") + " at or below the low-stock threshold"
                        + (out > 0 ? " (" + out + " out of stock)" : "");
        return new LowStockResponse(!low.isEmpty(), low.size(), out, message,
                low.stream().map(InventoryMapper::toPart).toList());
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse summary(AuthenticatedAccount caller) {
        Long profileId = ownProfile(caller).getId();
        List<SparePart> all = parts.findByRepairerProfile_Id(profileId);

        int active = 0;
        int discontinued = 0;
        int lowCount = 0;
        int outCount = 0;
        long units = 0;
        long value = 0;
        Map<SparePartCategory, long[]> byCategory = new EnumMap<>(SparePartCategory.class); // parts, units, value
        for (SparePart p : all) {
            if (p.getStatus() == SparePartStatus.ACTIVE) {
                active++;
                if (p.isLowStock()) {
                    lowCount++;
                }
                if (p.getQuantity() == 0) {
                    outCount++;
                }
            } else {
                discontinued++;
            }
            long partValue = p.stockValuePaise();
            units += p.getQuantity();
            value = Math.addExact(value, partValue);
            long[] totals = byCategory.computeIfAbsent(p.getCategory(), c -> new long[3]);
            totals[0]++;
            totals[1] += p.getQuantity();
            totals[2] = Math.addExact(totals[2], partValue);
        }
        List<CategorySummary> categories = byCategory.entrySet().stream()
                .map(e -> new CategorySummary(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        Money.toRupees(e.getValue()[2])))
                .sorted(Comparator.comparingLong(CategorySummary::valuePaise).reversed()
                        .thenComparing(c -> c.category().name()))
                .toList();
        long purchased = orZero(movements.sumCostPaise(profileId, InventoryMovementType.STOCK_IN));
        long usedInRepairs = orZero(movements.sumCostPaise(profileId, InventoryMovementType.USED_IN_REPAIR));
        return new InventorySummaryResponse(profileId, active, discontinued, units, lowCount, outCount, lowCount > 0,
                value, Money.toRupees(value), Money.format(value), purchased, usedInRepairs, false, VALUATION_NOTE,
                categories);
    }

    @Transactional(readOnly = true)
    public RepairPartsUsageResponse repairUsage(AuthenticatedAccount caller, Long repairRequestId) {
        RepairerProfile profile = ownProfile(caller);
        RepairRequest repair = ownRepair(profile, repairRequestId);
        List<InventoryMovement> linked = movements
                .findByRepairRequest_IdAndRepairerProfile_IdOrderByMovementDateAscIdAsc(repair.getId(), profile.getId());
        int netUnits = 0;
        long netCost = 0;
        for (InventoryMovement m : linked) {
            netUnits -= m.getQuantityChange();
            long cost = m.getUnitCostPaise() == null ? 0L : m.getUnitCostPaise();
            netCost = Math.subtractExact(netCost, Math.multiplyExact((long) m.getQuantityChange(), cost));
        }
        return new RepairPartsUsageResponse(repair.getId(), repair.getRequestNumber(), netUnits, netCost,
                Money.toRupees(netCost), linked.stream().map(InventoryMapper::toMovement).toList());
    }

    /* ─────────────────────────────── internals ─────────────────────────────── */

    /** The single place where a quantity changes: applies the delta and appends the movement. */
    private InventoryMovement record(SparePart p, InventoryMovementType type, int delta, long unitCostPaise,
                                     RepairRequest repair, String note, LocalDateTime movementDate) {
        long next = (long) p.getQuantity() + delta;
        if (next < 0) {
            throw new InsufficientStockException(p.getId(), p.getPartName(), p.getQuantity(), -delta);
        }
        if (next > SparePart.MAX_QUANTITY) {
            throw new RequestValidationException(Map.of("quantity",
                    "stock of one part can't exceed " + SparePart.MAX_QUANTITY + " units"));
        }
        int after = p.changeQuantity(delta);

        InventoryMovement m = new InventoryMovement();
        m.setRepairerProfile(p.getRepairerProfile());
        m.setSparePart(p);
        m.setRepairRequest(repair);
        m.setMovementType(type);
        m.setQuantityChange(delta);
        m.setStockAfter(after);
        m.setUnitCostPaise(unitCostPaise);
        m.setNote(note);
        m.setMovementDate(movementDate);
        return movements.save(m);
    }

    private StockChangeResponse changed(SparePart p, InventoryMovement m) {
        parts.saveAndFlush(p);
        return new StockChangeResponse(InventoryMapper.toPart(p), InventoryMapper.toMovement(m),
                InventoryMapper.raisesAlert(p), InventoryMapper.alertMessage(p));
    }

    /** (q1·c1 + q2·c2) / (q1 + q2), rounded half-up to whole paise. */
    static long weightedAverage(int onHand, long onHandCost, int received, long receivedCost) {
        if (onHand <= 0) {
            return receivedCost;
        }
        BigDecimal total = BigDecimal.valueOf(onHand).multiply(BigDecimal.valueOf(onHandCost))
                .add(BigDecimal.valueOf(received).multiply(BigDecimal.valueOf(receivedCost)));
        return total.divide(BigDecimal.valueOf((long) onHand + received), 0, RoundingMode.HALF_UP).longValueExact();
    }

    private RepairerProfile ownProfile(AuthenticatedAccount caller) {
        if (caller == null || !caller.isRepairer()) {
            throw new AccessDeniedException("Only repairers can manage a spare-parts inventory");
        }
        return profiles.findByAccountExternalId(caller.accountId()).orElseThrow(ProfileNotFoundException::noOwnProfile);
    }

    private SparePart ownedPart(RepairerProfile profile, Long id) {
        return parts.findByIdAndRepairerProfile_Id(id, profile.getId()).orElseThrow(() -> new SparePartNotFoundException(id));
    }

    private SparePart lockedPart(RepairerProfile profile, Long id) {
        return parts.findOwnedForUpdate(id, profile.getId()).orElseThrow(() -> new SparePartNotFoundException(id));
    }

    private RepairRequest ownRepair(RepairerProfile profile, Long repairRequestId) {
        RepairRequest r = requests.findById(repairRequestId)
                .orElseThrow(() -> new RepairRequestNotFoundException(repairRequestId));
        if (r.getRepairerProfile() == null || !Objects.equals(r.getRepairerProfile().getId(), profile.getId())) {
            throw new RepairRequestNotFoundException(repairRequestId);
        }
        return r;
    }

    private String uniqueSku(Long profileId, SparePartCategory category) {
        for (int i = 0; i < MAX_SKU_ATTEMPTS; i++) {
            String candidate = skus.next(category);
            if (!parts.existsByRepairerProfile_IdAndSku(profileId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique SKU");
    }

    static String normaliseSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new RequestValidationException(Map.of(field, message));
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
