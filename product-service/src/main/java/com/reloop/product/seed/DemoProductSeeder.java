package com.reloop.product.seed;

import com.reloop.product.dto.product.CreateProductRequest;
import com.reloop.product.dto.product.ProductResponse;
import com.reloop.product.dto.product.UpdateProductRequest;
import com.reloop.product.entity.Account;
import com.reloop.product.entity.City;
import com.reloop.product.entity.Store;
import com.reloop.product.enums.ProductCategory;
import com.reloop.product.enums.ProductCondition;
import com.reloop.product.repository.AccountRepository;
import com.reloop.product.repository.CityRepository;
import com.reloop.product.repository.ProductRepository;
import com.reloop.product.repository.StoreRepository;
import com.reloop.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds demo marketplace listings in Ahmedabad, Surat, Vadodara, Rajkot and Gandhinagar.
 *
 * <ul>
 *   <li>Runs after {@link DemoAccountSeeder} (same {@code reloop.seed.enabled=true} switch) and
 *       uses the three demo customers as sellers.</li>
 *   <li>Goes through {@link ProductService}, so validation, passports and history events are
 *       created exactly as for real listings.</li>
 *   <li>One listing is sold during seeding (Asha's pressure cooker, after a price drop, bought
 *       by Karan), so a passport with a real LISTED, PRICE_CHANGED, SOLD history exists.</li>
 *   <li>Idempotent: does nothing if the demo customer already has listings.</li>
 *   <li>Images are bundled category placeholders (no real image hosting yet).</li>
 * </ul>
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "reloop.seed.enabled", havingValue = "true")
public class DemoProductSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoProductSeeder.class);

    /** City name, latitude, longitude (all in Gujarat). */
    static final List<Object[]> CITIES = List.of(
            new Object[] {"Ahmedabad", 23.0225, 72.5714},
            new Object[] {"Surat", 21.1702, 72.8311},
            new Object[] {"Vadodara", 22.3072, 73.1812},
            new Object[] {"Rajkot", 22.3039, 70.8022},
            new Object[] {"Gandhinagar", 23.2156, 72.6369});

    static final String DEMO_STORE_NAME = "Karan's Pre-Loved Electronics";
    static final String SOLD_DEMO_TITLE = "Prestige Popular 5 L pressure cooker";

    /** One demo listing. {@code fromStore} = list it in Karan's demo store. */
    record DemoListing(String sellerEmail, String title, String description, ProductCategory category,
                       String price, ProductCondition condition, String city, String pickupLocation,
                       boolean fromStore) {
    }

    static final List<DemoListing> LISTINGS = List.of(
            // ── Ahmedabad (Asha) ──
            new DemoListing(DemoAccounts.CUSTOMER_EMAIL, "Apple iPhone 12, 128 GB, Blue",
                    "Battery health 86%, always used with a case and screen guard. Box and cable included.",
                    ProductCategory.ELECTRONICS, "28500.00", ProductCondition.VERY_GOOD, "Ahmedabad",
                    "Navrangpura, near Swastik Cross Roads", false),
            new DemoListing(DemoAccounts.CUSTOMER_EMAIL, "Sheesham wood study table with drawer",
                    "Solid sheesham wood, 4 ft x 2 ft. Minor scratches on the top, very sturdy.",
                    ProductCategory.FURNITURE, "4200.00", ProductCondition.GOOD, "Ahmedabad",
                    "Satellite, near Jodhpur Char Rasta", false),
            new DemoListing(DemoAccounts.CUSTOMER_EMAIL, SOLD_DEMO_TITLE,
                    "Aluminium cooker, new gasket fitted last month. Selling because we moved to induction.",
                    ProductCategory.HOME_KITCHEN, "1300.00", ProductCondition.LIKE_NEW, "Ahmedabad",
                    "Maninagar, near the railway station", false),
            // ── Surat (Karan, partly from his store) ──
            new DemoListing(DemoAccounts.KARAN_EMAIL, "Sony WH-1000XM4 noise-cancelling headphones",
                    "Excellent ANC, 30 h battery. Ear pads replaced. Comes with the original travel case.",
                    ProductCategory.ELECTRONICS, "13999.00", ProductCondition.VERY_GOOD, "Surat", null, true),
            new DemoListing(DemoAccounts.KARAN_EMAIL, "Dell Latitude 7490 laptop (i5 8th gen, 16 GB, 512 GB SSD)",
                    "Business laptop, fresh Windows install, keyboard backlight works. Charger included.",
                    ProductCategory.ELECTRONICS, "24000.00", ProductCondition.GOOD, "Surat", null, true),
            new DemoListing(DemoAccounts.KARAN_EMAIL, "Banarasi silk saree, worn once",
                    "Maroon with gold zari border, dry-cleaned, stored in a saree cover.",
                    ProductCategory.FASHION, "3800.00", ProductCondition.LIKE_NEW, "Surat",
                    "Adajan, near Star Bazaar", false),
            // ── Vadodara (Nisha) ──
            new DemoListing(DemoAccounts.NISHA_EMAIL, "Hero Sprint 26T geared bicycle",
                    "21-speed Shimano gears, new tyres. Seat cover slightly torn.",
                    ProductCategory.SPORTS_TOYS, "5500.00", ProductCondition.GOOD, "Vadodara",
                    "Alkapuri, near Race Course Circle", false),
            new DemoListing(DemoAccounts.NISHA_EMAIL, "NCERT Class 11-12 Science book set",
                    "Physics, Chemistry, Biology and Maths. Some pencil highlighting.",
                    ProductCategory.BOOKS, "900.00", ProductCondition.FAIR, "Vadodara",
                    "Fatehgunj, near MS University", false),
            new DemoListing(DemoAccounts.NISHA_EMAIL, "LG 6.5 kg top-load washing machine (needs drum belt)",
                    "Powers on, but the drum does not spin. Probably the belt. A good job for a ReLoop repairer.",
                    ProductCategory.APPLIANCES, "3000.00", ProductCondition.NEEDS_REPAIR, "Vadodara",
                    "Manjalpur", false),
            // ── Rajkot (Nisha) ──
            new DemoListing(DemoAccounts.NISHA_EMAIL, "Godrej steel almirah, 3 shelves",
                    "Double door with locker and both keys. Small dent on the side panel.",
                    ProductCategory.FURNITURE, "6500.00", ProductCondition.GOOD, "Rajkot",
                    "Kalawad Road, near Crystal Mall", false),
            new DemoListing(DemoAccounts.NISHA_EMAIL, "Canon EOS 1500D with 18-55 mm lens",
                    "About 4,000 shutter count, includes bag, 32 GB card and spare battery.",
                    ProductCategory.ELECTRONICS, "21000.00", ProductCondition.VERY_GOOD, "Rajkot",
                    "Yagnik Road", false),
            new DemoListing(DemoAccounts.NISHA_EMAIL, "Wooden toy kitchen set for kids",
                    "Pretend-play kitchen with utensils. Non-toxic paint, outgrown by our daughter.",
                    ProductCategory.SPORTS_TOYS, "1800.00", ProductCondition.LIKE_NEW, "Rajkot",
                    "University Road", false),
            // ── Gandhinagar (Asha) ──
            new DemoListing(DemoAccounts.CUSTOMER_EMAIL, "Kindle Paperwhite (10th generation, 8 GB)",
                    "Waterproof, warm light. Small scratch on the back, screen perfect.",
                    ProductCategory.ELECTRONICS, "6000.00", ProductCondition.GOOD, "Gandhinagar",
                    "Sector 21 market", false),
            new DemoListing(DemoAccounts.CUSTOMER_EMAIL, "Philips HD9200 air fryer, 4.1 L",
                    "Used for about a year, basket non-stick coating intact.",
                    ProductCategory.HOME_KITCHEN, "4500.00", ProductCondition.VERY_GOOD, "Gandhinagar",
                    "Infocity, Sector 7", false),
            new DemoListing(DemoAccounts.CUSTOMER_EMAIL, "Cricket kit bag with SG Kashmir willow bat",
                    "Bat, pads, gloves and helmet. The bat has a few seam marks.",
                    ProductCategory.SPORTS_TOYS, "2200.00", ProductCondition.FAIR, "Gandhinagar",
                    "Sector 11, near the GH-5 circle", false));

    private final ProductService productService;
    private final ProductRepository products;
    private final AccountRepository accounts;
    private final CityRepository cities;
    private final StoreRepository stores;

    public DemoProductSeeder(ProductService productService, ProductRepository products, AccountRepository accounts,
                             CityRepository cities, StoreRepository stores) {
        this.productService = productService;
        this.products = products;
        this.accounts = accounts;
        this.cities = cities;
        this.stores = stores;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<Account> asha = accounts.findByEmail(DemoAccounts.CUSTOMER_EMAIL);
        Optional<Account> karan = accounts.findByEmail(DemoAccounts.KARAN_EMAIL);
        Optional<Account> nisha = accounts.findByEmail(DemoAccounts.NISHA_EMAIL);
        if (asha.isEmpty() || karan.isEmpty() || nisha.isEmpty()) {
            log.warn("Demo customers missing; skipping demo products");
            return;
        }
        if (products.existsBySeller_Id(asha.get().getId())) {
            log.info("Demo products already present");
            return;
        }
        ensureCities();
        Store karanStore = ensureStore(karan.get());

        ProductResponse soldDemo = null;
        for (DemoListing listing : LISTINGS) {
            Account seller = accounts.findByEmail(listing.sellerEmail()).orElseThrow();
            ProductResponse created = productService.create(seller.getId(), toCreate(listing,
                    listing.fromStore() ? karanStore.getId() : null));
            if (SOLD_DEMO_TITLE.equals(listing.title())) {
                soldDemo = created;
            }
        }

        // A realistic passport: price drop, then bought by another demo customer.
        if (soldDemo != null) {
            DemoListing cooker = LISTINGS.stream().filter(l -> SOLD_DEMO_TITLE.equals(l.title())).findFirst().orElseThrow();
            productService.update(asha.get().getId(), soldDemo.id(), new UpdateProductRequest(
                    cooker.title(), cooker.description(), cooker.category(), new BigDecimal("1100.00"),
                    cooker.condition(), cooker.city(), cooker.pickupLocation(), null, null, null));
            productService.purchase(karan.get().getId(), soldDemo.id());
        }
        log.warn("Seeded {} demo listings across Ahmedabad, Surat, Vadodara, Rajkot and Gandhinagar", LISTINGS.size());
    }

    private void ensureCities() {
        for (Object[] c : CITIES) {
            String name = (String) c[0];
            City city = cities.findFirstByNameIgnoreCase(name).orElseGet(() -> new City(name, "Gujarat"));
            if (city.getLatitude() == null) {
                city.setLatitude((Double) c[1]);
                city.setLongitude((Double) c[2]);
            }
            if ("Unspecified".equals(city.getState())) {
                city.setState("Gujarat");
            }
            cities.save(city);
        }
    }

    private Store ensureStore(Account owner) {
        return stores.findByOwner_Id(owner.getId()).stream().findFirst().orElseGet(() -> {
            Store store = new Store();
            store.setOwner(owner);
            store.setName(DEMO_STORE_NAME);
            store.setDescription("Tested second-hand phones, laptops and audio gear.");
            store.setAddressLine("Shop 4, Ghod Dod Road, Surat 395007");
            store.setCity(cities.findFirstByNameIgnoreCase("Surat").orElse(null));
            return stores.save(store);
        });
    }

    static CreateProductRequest toCreate(DemoListing l, Long storeId) {
        return new CreateProductRequest(l.title(), l.description(), l.category(), new BigDecimal(l.price()),
                l.condition(), l.city(), l.pickupLocation(), storeId, null, null);
    }
}
