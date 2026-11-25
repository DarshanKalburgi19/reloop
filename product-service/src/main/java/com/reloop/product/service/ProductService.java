package com.reloop.product.service;

import com.reloop.product.dto.product.CreateProductRequest;
import com.reloop.product.dto.product.PageResponse;
import com.reloop.product.dto.product.PassportEventResponse;
import com.reloop.product.dto.product.PassportResponse;
import com.reloop.product.dto.product.ProductResponse;
import com.reloop.product.dto.product.ProductSearchCriteria;
import com.reloop.product.dto.product.ProductSummaryResponse;
import com.reloop.product.dto.product.PurchaseResponse;
import com.reloop.product.dto.product.UpdateProductRequest;
import com.reloop.product.entity.Account;
import com.reloop.product.entity.Product;
import com.reloop.product.entity.ProductHistoryEvent;
import com.reloop.product.entity.ProductImage;
import com.reloop.product.entity.ProductPassport;
import com.reloop.product.entity.Store;
import com.reloop.product.enums.AccountStatus;
import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.enums.ProductHistoryEventType;
import com.reloop.product.enums.ProductStatus;
import com.reloop.product.enums.StoreStatus;
import com.reloop.product.exception.AccountNotActiveException;
import com.reloop.product.exception.AccountNotFoundException;
import com.reloop.product.exception.FieldValidationException;
import com.reloop.product.exception.ListingOwnershipException;
import com.reloop.product.exception.ListingStateException;
import com.reloop.product.exception.OwnProductPurchaseException;
import com.reloop.product.exception.ProductNotAvailableException;
import com.reloop.product.exception.ProductNotFoundException;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.CityRepository;
import com.reloop.product.repository.ProductHistoryEventRepository;
import com.reloop.product.repository.ProductPassportRepository;
import com.reloop.product.repository.ProductRepository;
import com.reloop.product.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second-hand marketplace: listings, browse/search, passport and purchase.
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>Only the seller can edit, deactivate or reactivate a listing (403 otherwise).</li>
 *   <li>Sold and reserved listings are frozen (409). Delete is a <b>soft</b> delete (ARCHIVED),
 *       so the passport history is never lost.</li>
 *   <li>Browse and search show only ACTIVE listings. ARCHIVED and DRAFT listings are visible
 *       only to their seller (404 for everyone else).</li>
 *   <li>A purchase locks the product row: sellers can't buy their own listing (403), and only
 *       ACTIVE listings can be bought (409). Passport ownership moves to the buyer.</li>
 *   <li>Every change appends a {@link ProductHistoryEvent} to the item's passport.</li>
 * </ul>
 */
@Service
public class ProductService {

    /** Statuses that count as "an open listing" for a passport. */
    static final Set<ProductStatus> OPEN_STATUSES = EnumSet.of(ProductStatus.DRAFT, ProductStatus.ACTIVE, ProductStatus.RESERVED);
    /** Statuses anybody may view. */
    static final Set<ProductStatus> PUBLIC_STATUSES = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.RESERVED, ProductStatus.SOLD);

    static final String PURCHASE_NOTE =
            "Purchase recorded and passport ownership transferred. Order, simulated payment and commission "
                    + "records are added by order-service in the orders & payments phase.";

    private final ProductRepository products;
    private final ProductPassportRepository passports;
    private final ProductHistoryEventRepository history;
    private final AccountRepository accounts;
    private final StoreRepository stores;
    private final CityRepository cities;
    private final PassportCodeGenerator codeGenerator;

    public ProductService(ProductRepository products, ProductPassportRepository passports,
                          ProductHistoryEventRepository history, AccountRepository accounts,
                          StoreRepository stores, CityRepository cities, PassportCodeGenerator codeGenerator) {
        this.products = products;
        this.passports = passports;
        this.history = history;
        this.accounts = accounts;
        this.stores = stores;
        this.cities = cities;
        this.codeGenerator = codeGenerator;
    }

    /* ───────────────────────────── create ───────────────────────────── */

    @Transactional
    public ProductResponse create(Long sellerId, CreateProductRequest req) {
        Account seller = activeAccount(sellerId);

        ProductPassport passport;
        ProductHistoryEventType firstEvent;
        if (req.passportCode() == null || req.passportCode().isBlank()) {
            passport = passports.save(new ProductPassport(codeGenerator.generateUnique(), seller));
            firstEvent = ProductHistoryEventType.LISTED;
        } else {
            passport = passportForRelisting(seller, req.passportCode());
            firstEvent = ProductHistoryEventType.RELISTED;
        }

        Product product = new Product();
        product.setSeller(seller);
        product.setPassport(passport);
        product.setStatus(ProductStatus.ACTIVE);
        applyEditableFields(product, seller, req.title(), req.description(), req.category(), Money.toPaise(req.price()),
                req.condition(), req.city(), req.pickupLocation(), req.storeId(), req.imageUrls());
        product = products.save(product);

        String details = firstEvent == ProductHistoryEventType.LISTED
                ? "Listed for sale: " + product.getTitle() + " (" + product.getCondition() + ", " + product.getLocationCity() + ")"
                : "Relisted by its new owner: " + product.getTitle() + " (" + product.getCondition() + ", " + product.getLocationCity() + ")";
        record(product, firstEvent, seller, details);
        return ProductMapper.toResponse(product);
    }

    /* ───────────────────────────── update ───────────────────────────── */

    @Transactional
    public ProductResponse update(Long actorId, Long productId, UpdateProductRequest req) {
        Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        Account actor = requireOwner(product, actorId, "You can only edit your own listings");
        requireEditable(product, "edited");
        if (req.status() != null && req.status() != ProductStatus.ACTIVE && req.status() != ProductStatus.ARCHIVED) {
            throw new FieldValidationException("status", "must be ACTIVE or ARCHIVED");
        }

        long oldPrice = product.getPricePaise();
        List<String> changed = changedFields(product, req);

        applyEditableFields(product, actor, req.title(), req.description(), req.category(), Money.toPaise(req.price()),
                req.condition(), req.city(), req.pickupLocation(), req.storeId(), req.imageUrls());

        if (oldPrice != product.getPricePaise()) {
            record(product, ProductHistoryEventType.PRICE_CHANGED, actor,
                    "Price changed from " + Money.format(oldPrice) + " to " + Money.format(product.getPricePaise()));
        }
        if (!changed.isEmpty()) {
            record(product, ProductHistoryEventType.UPDATED, actor, "Updated: " + String.join(", ", changed));
        }
        if (req.status() != null && req.status() != product.getStatus()) {
            if (req.status() == ProductStatus.ARCHIVED) {
                archive(product, actor);
            } else {
                reactivate(product, actor);
            }
        }
        products.flush(); // make updatedAt reflect this request in the response
        return ProductMapper.toResponse(product);
    }

    /* ─────────────────────── delete (soft) / deactivate ─────────────────────── */

    /** DELETE: soft delete. Idempotent for listings that are already deactivated. */
    @Transactional
    public void deactivate(Long actorId, Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        Account actor = requireOwner(product, actorId, "You can only delete your own listings");
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            return;
        }
        if (product.getStatus() == ProductStatus.SOLD) {
            throw new ListingStateException("Sold listings are kept for the product passport and cannot be deleted");
        }
        requireEditable(product, "deleted");
        archive(product, actor);
    }

    /* ───────────────────────────── read ───────────────────────────── */

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> browse(ProductSearchCriteria criteria) {
        PageRequest page = PageRequest.of(criteria.pageOrDefault(), criteria.sizeOrDefault(), sortFor(criteria.sortOrDefault()));
        return PageResponse.of(products.findAll(ProductSpecifications.availableMatching(criteria), page),
                ProductMapper::toSummary);
    }

    /** The caller's own listings in every status, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> mine(Long sellerId, int page, int size) {
        return PageResponse.of(products.findBySeller_IdOrderByCreatedAtDesc(sellerId, PageRequest.of(page, size)),
                ProductMapper::toSummary);
    }

    /** @param viewerId authenticated account id, or null for anonymous visitors */
    @Transactional(readOnly = true)
    public ProductResponse get(Long productId, Long viewerId) {
        return ProductMapper.toResponse(visibleProduct(productId, viewerId));
    }

    @Transactional(readOnly = true)
    public PassportResponse passport(Long productId, Long viewerId) {
        Product product = visibleProduct(productId, viewerId);
        ProductPassport passport = product.getPassport();
        List<Product> listings = products.findByPassport_IdOrderByCreatedAtAscIdAsc(passport.getId());
        List<ProductHistoryEvent> events = history.findByPassport_IdOrderByCreatedAtAscIdAsc(passport.getId());

        List<PassportEventResponse> timeline = events.stream().map(e -> new PassportEventResponse(
                e.getEventType(),
                e.getCreatedAt(),
                e.getProduct().getId(),
                actorRole(e),
                e.getPricePaise() != null ? Money.toRupees(e.getPricePaise()) : null,
                e.getDetails())).toList();
        int transfers = (int) events.stream().filter(e -> e.getEventType() == ProductHistoryEventType.SOLD).count();
        LocalDateTime issuedAt = passport.getCreatedAt();

        return new PassportResponse(passport.getPassportCode(), product.getId(), product.getTitle(),
                product.getCategory(), product.getCondition(), product.getStatus(), issuedAt, listings.size(),
                transfers, listings.stream().map(Product::getId).toList(), timeline);
    }

    /* ───────────────────────────── purchase ───────────────────────────── */

    @Transactional
    public PurchaseResponse purchase(Long buyerId, Long productId) {
        Account buyer = activeAccount(buyerId);
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));

        if (Objects.equals(product.getSeller().getId(), buyerId)) {
            throw new OwnProductPurchaseException();
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductNotAvailableException(product.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        product.setStatus(ProductStatus.SOLD);
        product.setSoldAt(now);
        product.setBuyer(buyer);
        product.getPassport().setCurrentOwner(buyer);
        record(product, ProductHistoryEventType.SOLD, buyer,
                "Sold for " + Money.format(product.getPricePaise()) + "; passport ownership transferred to the buyer");

        return new PurchaseResponse(product.getId(), product.getTitle(), product.getPassport().getPassportCode(),
                product.getSeller().getId(), buyer.getId(), Money.toRupees(product.getPricePaise()),
                product.getPricePaise(), Money.CURRENCY, product.getStatus(), now, PURCHASE_NOTE);
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private Account activeAccount(Long accountId) {
        Account account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getStatus().name());
        }
        return account;
    }

    private Account requireOwner(Product product, Long actorId, String message) {
        if (!Objects.equals(product.getSeller().getId(), actorId)) {
            // hidden listings stay hidden: a non-owner gets the same 404 as for a missing id
            if (!PUBLIC_STATUSES.contains(product.getStatus())) {
                throw new ProductNotFoundException(product.getId());
            }
            throw new ListingOwnershipException(message);
        }
        return activeAccount(actorId);
    }

    private static void requireEditable(Product product, String verb) {
        if (product.getStatus() == ProductStatus.SOLD) {
            throw new ListingStateException("Sold listings cannot be " + verb);
        }
        if (product.getStatus() == ProductStatus.RESERVED) {
            throw new ListingStateException("Reserved listings cannot be " + verb + " while a purchase is in progress");
        }
    }

    private Product visibleProduct(Long productId, Long viewerId) {
        Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        boolean owner = viewerId != null && Objects.equals(product.getSeller().getId(), viewerId);
        if (!owner && !PUBLIC_STATUSES.contains(product.getStatus())) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }

    private ProductPassport passportForRelisting(Account seller, String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        ProductPassport passport = passports.findByPassportCode(code)
                .orElseThrow(() -> new FieldValidationException("passportCode", "unknown passport code"));
        if (!Objects.equals(passport.getCurrentOwner().getId(), seller.getId())) {
            throw new ListingOwnershipException("Only the current owner of this item can list it");
        }
        if (products.existsByPassport_IdAndStatusIn(passport.getId(), OPEN_STATUSES)) {
            throw new ListingStateException("This item already has an open listing");
        }
        return passport;
    }

    private void archive(Product product, Account actor) {
        product.setStatus(ProductStatus.ARCHIVED);
        record(product, ProductHistoryEventType.DEACTIVATED, actor, "Listing deactivated by the seller");
    }

    private void reactivate(Product product, Account actor) {
        ProductPassport passport = product.getPassport();
        if (!Objects.equals(passport.getCurrentOwner().getId(), actor.getId())) {
            throw new ListingStateException("You no longer own this item, so the listing cannot be reactivated");
        }
        if (products.existsByPassport_IdAndStatusInAndIdNot(passport.getId(), OPEN_STATUSES, product.getId())) {
            throw new ListingStateException("This item already has another open listing");
        }
        product.setStatus(ProductStatus.ACTIVE);
        record(product, ProductHistoryEventType.REACTIVATED, actor, "Listing reactivated by the seller");
    }

    private void applyEditableFields(Product product, Account seller, String title, String description,
                                     ProductCategory category, long pricePaise, ProductCondition condition,
                                     String city, String pickupLocation, Long storeId, List<String> imageUrls) {
        product.setTitle(title.trim());
        product.setDescription(description.trim());
        product.setCategory(category);
        product.setPricePaise(pricePaise);
        product.setCondition(condition);
        product.setLocationCity(canonicalCity(city));
        product.setPickupLocation(blankToNull(pickupLocation));
        product.setStore(resolveStore(seller, storeId));
        if (!sameImages(product, imageUrls)) {
            product.replaceImages(buildImages(product, imageUrls));
        }
    }

    private Store resolveStore(Account seller, Long storeId) {
        if (storeId == null) {
            return null;
        }
        Store store = stores.findById(storeId)
                .orElseThrow(() -> new FieldValidationException("storeId", "store not found"));
        if (!Objects.equals(store.getOwner().getId(), seller.getId())) {
            throw new FieldValidationException("storeId", "you can only list products in your own store");
        }
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new FieldValidationException("storeId", "store is closed");
        }
        return store;
    }

    /** "surat" -> "Surat" when the city is known; otherwise the trimmed input. */
    private String canonicalCity(String city) {
        String trimmed = city.trim();
        return cities.findFirstByNameIgnoreCase(trimmed).map(c -> c.getName()).orElse(trimmed);
    }

    private static List<String> effectiveImageUrls(ProductCategory category, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of(PlaceholderImages.forCategory(category));
        }
        return imageUrls.stream().map(String::trim).toList();
    }

    private static boolean sameImages(Product product, List<String> imageUrls) {
        if (product.getImages().isEmpty()) {
            return false;
        }
        List<String> current = product.getImages().stream().map(ProductImage::getUrl).toList();
        return current.equals(effectiveImageUrls(product.getCategory(), imageUrls));
    }

    private static List<ProductImage> buildImages(Product product, List<String> imageUrls) {
        List<String> urls = effectiveImageUrls(product.getCategory(), imageUrls);
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setUrl(urls.get(i));
            image.setAltText(PlaceholderImages.isPlaceholder(urls.get(i))
                    ? "Placeholder image for " + product.getCategory()
                    : product.getTitle() + " - photo " + (i + 1));
            image.setSortOrder(i);
            image.setPrimary(i == 0);
            images.add(image);
        }
        return images;
    }

    /** Field names (excluding price and status, which get their own events) that the request changes. */
    private List<String> changedFields(Product p, UpdateProductRequest r) {
        List<String> changed = new ArrayList<>();
        if (!p.getTitle().equals(r.title().trim())) {
            changed.add("title");
        }
        if (!Objects.equals(p.getDescription(), r.description().trim())) {
            changed.add("description");
        }
        if (p.getCategory() != r.category()) {
            changed.add("category");
        }
        if (p.getCondition() != r.condition()) {
            changed.add("condition");
        }
        if (!Objects.equals(p.getLocationCity(), canonicalCity(r.city()))) {
            changed.add("city");
        }
        if (!Objects.equals(p.getPickupLocation(), blankToNull(r.pickupLocation()))) {
            changed.add("pickupLocation");
        }
        Long currentStore = p.getStore() != null ? p.getStore().getId() : null;
        if (!Objects.equals(currentStore, r.storeId())) {
            changed.add("store");
        }
        List<String> currentImages = p.getImages().stream().map(ProductImage::getUrl).toList();
        if (!currentImages.equals(effectiveImageUrls(r.category(), r.imageUrls()))) {
            changed.add("images");
        }
        return changed;
    }

    private void record(Product product, ProductHistoryEventType type, Account actor, String details) {
        String safeDetails = details.length() > 500 ? details.substring(0, 497) + "..." : details;
        history.save(new ProductHistoryEvent(product.getPassport(), product, type, actor, product.getPricePaise(), safeDetails));
    }

    private static String actorRole(ProductHistoryEvent e) {
        if (e.getActor() == null) {
            return "SYSTEM";
        }
        return Objects.equals(e.getActor().getId(), e.getProduct().getSeller().getId()) ? "SELLER" : "BUYER";
    }

    private static Sort sortFor(String sort) {
        return switch (sort) {
            case "oldest" -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case "price_asc" -> Sort.by(Sort.Order.asc("pricePaise"), Sort.Order.desc("id"));
            case "price_desc" -> Sort.by(Sort.Order.desc("pricePaise"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
