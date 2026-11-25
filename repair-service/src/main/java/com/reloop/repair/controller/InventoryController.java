package com.reloop.repair.controller;

import com.reloop.repair.dto.inventory.AdjustStockRequest;
import com.reloop.repair.dto.inventory.CreateSparePartRequest;
import com.reloop.repair.dto.inventory.InventoryMovementResponse;
import com.reloop.repair.dto.inventory.InventorySummaryResponse;
import com.reloop.repair.dto.inventory.LowStockResponse;
import com.reloop.repair.dto.inventory.RepairPartsUsageResponse;
import com.reloop.repair.dto.inventory.SparePartResponse;
import com.reloop.repair.dto.inventory.StockChangeResponse;
import com.reloop.repair.dto.inventory.StockInRequest;
import com.reloop.repair.dto.inventory.UpdateSparePartRequest;
import com.reloop.repair.dto.inventory.UsePartRequest;
import com.reloop.repair.dto.repairer.PageResponse;
import com.reloop.repair.enums.InventoryMovementType;
import com.reloop.repair.enums.SparePartCategory;
import com.reloop.repair.security.AuthenticatedAccount;
import com.reloop.repair.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repairer spare-parts inventory (Bearer JWT, REPAIRER role, own workshop only; see docs/INVENTORY.md).
 *
 * <pre>
 * Parts
 *   GET   /api/inventory/parts                     list (?category=&q=&lowStockOnly=&includeDiscontinued=&sort=&page=&size=)
 *   POST  /api/inventory/parts                     add a part (+ optional opening stock)       201 + Location
 *   GET   /api/inventory/parts/{id}                details
 *   PATCH /api/inventory/parts/{id}                edit info / unit cost / low-stock threshold
 *   POST  /api/inventory/parts/{id}/discontinue    hide from active list, stop stock-in
 *   POST  /api/inventory/parts/{id}/reactivate
 * Stock movements (each one is recorded)
 *   POST  /api/inventory/parts/{id}/stock-in       STOCK_IN        {quantity, unitCost?, note?, movementDate?}
 *   POST  /api/inventory/parts/{id}/use            USED_IN_REPAIR  {repairRequestId, quantity, note?}
 *   POST  /api/inventory/parts/{id}/adjust         ADJUSTMENT      {newQuantity | quantityChange, reason, repairRequestId?}
 *   GET   /api/inventory/parts/{id}/movements      one part's ledger
 *   GET   /api/inventory/movements                 all movements (?type=&sparePartId=&repairRequestId=&from=&to=)
 * Reports
 *   GET   /api/inventory/summary                   totals, low-stock alert, inventory value (not repair earnings)
 *   GET   /api/inventory/low-stock                 low-stock alert list
 *   GET   /api/inventory/repair-requests/{id}/parts parts used on one repair
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('REPAIRER')")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    /* ─────────────────────────────── parts ─────────────────────────────── */

    @GetMapping("/parts")
    public PageResponse<SparePartResponse> list(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                @RequestParam(required = false) SparePartCategory category,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "false") boolean lowStockOnly,
                                                @RequestParam(defaultValue = "false") boolean includeDiscontinued,
                                                @RequestParam(defaultValue = "name") String sort,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(principal, category, q, lowStockOnly, includeDiscontinued, sort, page, size);
    }

    @PostMapping(path = "/parts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SparePartResponse> create(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                    @Valid @RequestBody CreateSparePartRequest request) {
        SparePartResponse created = service.create(principal, request);
        return ResponseEntity.created(URI.create("/api/inventory/parts/" + created.id())).body(created);
    }

    @GetMapping("/parts/{id:\\d+}")
    public SparePartResponse get(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id) {
        return service.get(principal, id);
    }

    @PatchMapping(path = "/parts/{id:\\d+}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SparePartResponse update(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                    @Valid @RequestBody UpdateSparePartRequest request) {
        return service.update(principal, id, request);
    }

    @PostMapping("/parts/{id:\\d+}/discontinue")
    public SparePartResponse discontinue(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id) {
        return service.discontinue(principal, id);
    }

    @PostMapping("/parts/{id:\\d+}/reactivate")
    public SparePartResponse reactivate(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id) {
        return service.reactivate(principal, id);
    }

    /* ─────────────────────────────── stock movements ─────────────────────────────── */

    @PostMapping(path = "/parts/{id:\\d+}/stock-in", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StockChangeResponse stockIn(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                       @Valid @RequestBody StockInRequest request) {
        return service.stockIn(principal, id, request);
    }

    @PostMapping(path = "/parts/{id:\\d+}/use", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StockChangeResponse use(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                   @Valid @RequestBody UsePartRequest request) {
        return service.use(principal, id, request);
    }

    @PostMapping(path = "/parts/{id:\\d+}/adjust", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StockChangeResponse adjust(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable Long id,
                                      @Valid @RequestBody AdjustStockRequest request) {
        return service.adjust(principal, id, request);
    }

    @GetMapping("/parts/{id:\\d+}/movements")
    public PageResponse<InventoryMovementResponse> partMovements(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                                 @PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.partMovements(principal, id, page, size);
    }

    @GetMapping("/movements")
    public PageResponse<InventoryMovementResponse> movements(
            @AuthenticationPrincipal AuthenticatedAccount principal,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) Long sparePartId,
            @RequestParam(required = false) Long repairRequestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.movements(principal, type, sparePartId, repairRequestId, from, to, page, size);
    }

    /* ─────────────────────────────── reports ─────────────────────────────── */

    @GetMapping("/summary")
    public InventorySummaryResponse summary(@AuthenticationPrincipal AuthenticatedAccount principal) {
        return service.summary(principal);
    }

    @GetMapping("/low-stock")
    public LowStockResponse lowStock(@AuthenticationPrincipal AuthenticatedAccount principal) {
        return service.lowStock(principal);
    }

    @GetMapping("/repair-requests/{repairRequestId:\\d+}/parts")
    public RepairPartsUsageResponse repairUsage(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                @PathVariable Long repairRequestId) {
        return service.repairUsage(principal, repairRequestId);
    }
}
