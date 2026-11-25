package com.reloop.repair.controller;

import com.reloop.repair.dto.repairer.NearbyResponse;
import com.reloop.repair.dto.repairer.NearbySearchCriteria;
import com.reloop.repair.dto.repairer.PageResponse;
import com.reloop.repair.dto.repairer.RepairerDetailResponse;
import com.reloop.repair.dto.repairer.RepairerProfileRequest;
import com.reloop.repair.dto.repairer.RepairerSearchCriteria;
import com.reloop.repair.dto.repairer.RepairerSummaryResponse;
import com.reloop.repair.security.AuthenticatedAccount;
import com.reloop.repair.service.RepairerSearchService;
import com.reloop.repair.service.WorkshopProfileService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repairer service-provider API.
 *
 * <pre>
 * POST /api/repairers           REPAIRER  create own workshop profile (201 + Location; 409 if one exists)
 * GET  /api/repairers           public    search: city, category, minPrice/maxPrice, method, availableOnly, q, sort, paging
 * GET  /api/repairers/nearby    public    workshops near lat/lng within radiusKm, nearest first (Leaflet-ready)
 * GET  /api/repairers/me        REPAIRER  own profile (any status, incl. what is missing for search)
 * GET  /api/repairers/{id}      public    public workshop profile: location, methods, services, hours, ratings
 * PUT  /api/repairers/{id}      REPAIRER  edit own profile (full replacement; 403 for other repairers)
 * </pre>
 *
 * <p>Tokens come from product-service ({@code POST /api/auth/login}). Role checks happen in
 * SecurityConfig and again via {@code @PreAuthorize}; ownership is checked in the service.</p>
 */
@RestController
@RequestMapping(path = "/api/repairers", produces = MediaType.APPLICATION_JSON_VALUE)
public class RepairerController {

    private final WorkshopProfileService profileService;
    private final RepairerSearchService searchService;

    public RepairerController(WorkshopProfileService profileService, RepairerSearchService searchService) {
        this.profileService = profileService;
        this.searchService = searchService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public ResponseEntity<RepairerDetailResponse> create(@AuthenticationPrincipal AuthenticatedAccount principal,
                                                         @Valid @RequestBody RepairerProfileRequest request) {
        RepairerDetailResponse created = profileService.create(principal, request);
        return ResponseEntity.created(URI.create("/api/repairers/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<RepairerSummaryResponse> search(@Valid @ModelAttribute RepairerSearchCriteria criteria) {
        return searchService.search(criteria);
    }

    @GetMapping("/nearby")
    public NearbyResponse nearby(@Valid @ModelAttribute NearbySearchCriteria criteria) {
        return searchService.nearby(criteria);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairerDetailResponse mine(@AuthenticationPrincipal AuthenticatedAccount principal) {
        return profileService.mine(principal);
    }

    @GetMapping("/{id:\\d+}")
    public RepairerDetailResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal) {
        return profileService.get(id, principal);
    }

    @PutMapping(path = "/{id:\\d+}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('REPAIRER')")
    public RepairerDetailResponse update(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedAccount principal,
                                         @Valid @RequestBody RepairerProfileRequest request) {
        return profileService.update(principal, id, request);
    }
}
