package com.reloop.repair.seed;

import static com.reloop.repair.enums.PriceType.FIXED;
import static com.reloop.repair.enums.PriceType.STARTING_FROM;
import static com.reloop.repair.enums.RepairServiceCategory.BICYCLE;
import static com.reloop.repair.enums.RepairServiceCategory.ELECTRICAL;
import static com.reloop.repair.enums.RepairServiceCategory.HOME_APPLIANCE;
import static com.reloop.repair.enums.RepairServiceCategory.LAPTOP;
import static com.reloop.repair.enums.RepairServiceCategory.MOBILE;
import static com.reloop.repair.enums.RepairServiceCategory.OTHER;
import static com.reloop.repair.enums.RepairServiceCategory.TABLET;
import static com.reloop.repair.enums.RepairServiceCategory.TV;

import com.reloop.repair.dto.repairer.RepairerProfileRequest;
import com.reloop.repair.dto.repairer.ServiceOfferingRequest;
import com.reloop.repair.dto.repairer.WorkingHoursRequest;
import com.reloop.repair.enums.AvailabilityStatus;
import com.reloop.repair.enums.PriceType;
import com.reloop.repair.enums.RepairMethod;
import com.reloop.repair.enums.RepairServiceCategory;
import com.reloop.repair.enums.VerificationStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * DEMO DATA: ten fictional repair workshops in Gujarat with real neighbourhood coordinates, so search,
 * filters and the Leaflet map have something to show. Every workshop is named "... (Demo)", is stored with
 * {@code demo = true} (shown to customers as {@code demo: true} plus a notice), and uses a negative placeholder
 * account ID: these profiles have no login and can't be edited through the API. Phone numbers are fake.
 */
public final class DemoRepairers {

    /** Placeholder account IDs: -1001, -1002, ... (real product-service account IDs are positive). */
    public static final long FIRST_ACCOUNT_ID = -1001L;

    public record DemoReview(int rating, String reviewerName, String comment) {
    }

    public record DemoWorkshop(long accountExternalId, RepairerProfileRequest profile, VerificationStatus verification,
                               List<DemoReview> reviews) {
    }

    public static final List<DemoWorkshop> WORKSHOPS = List.of(
        workshop(0, new RepairerProfileRequest(
                "QuickFix Mobile Care (Demo)",
                "Phone and tablet repairs while you wait: screens, batteries, charging ports. Genuine and OEM-grade parts with a 90-day warranty.",
                Set.of(MOBILE, TABLET), List.of("Screen replacement", "Battery replacement", "Micro-soldering"),
                List.of(svc("Phone screen replacement", MOBILE, STARTING_FROM, "1499", 1),
                        svc("Phone battery replacement", MOBILE, FIXED, "899", 1),
                        svc("Charging port repair", MOBILE, FIXED, "499", 1),
                        svc("Tablet screen replacement", TABLET, STARTING_FROM, "2999", 3)),
                "+919800000101", "Shop 12, Super Mall, CG Road, Navrangpura", "Ahmedabad", "380009",
                23.0276, 72.5577, with(monToSat("10:00", "20:00"), day(DayOfWeek.SUNDAY, "11:00", "15:00")),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.PICKUP_DROPOFF), AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.VERIFIED,
            List.of(new DemoReview(5, "Priya S.", "Screen replaced in 40 minutes, looks brand new."),
                    new DemoReview(5, "Harsh P.", "Fair price and they showed me the old part."),
                    new DemoReview(4, "Meera J.", "Good work, had to wait a bit on Saturday."),
                    new DemoReview(5, "Rohan D.", "Battery life is back to normal."))),

        workshop(1, new RepairerProfileRequest(
                "Maninagar Laptop Clinic (Demo)",
                "Laptop specialists: screens, keyboards, hinges, motherboard diagnosis and data recovery. Send your laptop by courier or walk in.",
                Set.of(LAPTOP, TABLET), List.of("Motherboard repair", "Data recovery", "Windows and Linux"),
                List.of(svc("Laptop screen replacement", LAPTOP, STARTING_FROM, "3499", 3),
                        svc("Keyboard replacement", LAPTOP, STARTING_FROM, "1999", 2),
                        svc("OS reinstall with data backup", LAPTOP, FIXED, "799", 1),
                        svc("Motherboard diagnosis", LAPTOP, FIXED, "499", 2)),
                "+919800000102", "First floor, Jawahar Chowk, Maninagar", "Ahmedabad", "380008",
                22.9962, 72.6036, monToSat("10:30", "19:30"),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.SEND_ITEM), AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.VERIFIED,
            List.of(new DemoReview(4, "Kunal M.", "Recovered all my college files."),
                    new DemoReview(5, "Sneha R.", "Keyboard replaced next day, very professional."),
                    new DemoReview(4, "Amit T.", "Clear quote before starting the work."))),

        workshop(2, new RepairerProfileRequest(
                "Vastrapur Home Appliance Repairs (Demo)",
                "Washing machines, refrigerators, microwaves, LED TVs and home wiring. We pick up heavy appliances and drop them back after repair.",
                Set.of(HOME_APPLIANCE, TV, ELECTRICAL), List.of("Washing machines", "Refrigerators", "LED TV panels"),
                List.of(svc("Washing machine repair", HOME_APPLIANCE, STARTING_FROM, "599", 2),
                        svc("Refrigerator gas refill", HOME_APPLIANCE, STARTING_FROM, "1800", 1),
                        svc("LED TV panel repair", TV, STARTING_FROM, "2500", 4),
                        svc("House wiring safety check", ELECTRICAL, FIXED, "350", 1)),
                "+919800000103", "Near Vastrapur Lake, Vastrapur", "Ahmedabad", "380015",
                23.0395, 72.5293, daily("09:00", "21:00"),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.PICKUP_DROPOFF), AvailabilityStatus.BUSY,
                "High demand this week: pickups are scheduled within 2 days"),
            VerificationStatus.UNVERIFIED,
            List.of(new DemoReview(4, "Nirav B.", "Fridge cooling again, technician was polite."),
                    new DemoReview(3, "Pooja K.", "Repair was good but pickup was a day late."),
                    new DemoReview(5, "Vikram S.", "Fixed our washing machine drum at home."),
                    new DemoReview(4, "Divya A.", "Reasonable charges."))),

        workshop(3, new RepairerProfileRequest(
                "Sector 21 Gadget Doctor (Demo)",
                "Phones, laptops and TVs repaired in Gandhinagar. Courier your device from anywhere in Gujarat; we return it insured.",
                Set.of(MOBILE, LAPTOP, TV), List.of("Phones", "Laptop batteries", "TV backlights"),
                List.of(svc("Phone screen replacement", MOBILE, STARTING_FROM, "1299", 1),
                        svc("Laptop battery replacement", LAPTOP, STARTING_FROM, "2499", 2),
                        svc("TV backlight repair", TV, STARTING_FROM, "1999", 3)),
                "+919800000104", "Shop 7, Sector 21 Shopping Centre", "Gandhinagar", "382021",
                23.2316, 72.6452, with(monToFri("10:00", "19:00"), day(DayOfWeek.SATURDAY, "10:00", "14:00")),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.SEND_ITEM), AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.VERIFIED,
            List.of(new DemoReview(5, "Jignesh V.", "Couriered my laptop from Mehsana, came back in 3 days."),
                    new DemoReview(4, "Ritu G.", "TV works perfectly now."))),

        workshop(4, new RepairerProfileRequest(
                "Adajan Smart Repairs (Demo)",
                "iPhone and Android experts in Adajan. Walk in, send your phone, or book a free pickup anywhere in Surat.",
                Set.of(MOBILE, LAPTOP, TABLET), List.of("iPhone", "Water damage", "Laptop hinges"),
                List.of(svc("iPhone screen replacement", MOBILE, STARTING_FROM, "2999", 1),
                        svc("Android screen replacement", MOBILE, STARTING_FROM, "1199", 1),
                        svc("Water damage treatment", MOBILE, STARTING_FROM, "999", 2),
                        svc("Laptop hinge repair", LAPTOP, STARTING_FROM, "1499", 2),
                        svc("Tablet battery replacement", TABLET, FIXED, "1899", 2)),
                "+919800000105", "Shop 3, Star Plaza, Adajan", "Surat", "395009",
                21.1940, 72.7932, monToSat("10:00", "21:00"),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.SEND_ITEM, RepairMethod.PICKUP_DROPOFF),
                AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.VERIFIED,
            List.of(new DemoReview(5, "Mansi D.", "Saved my phone after it fell in water."),
                    new DemoReview(5, "Yash C.", "Pickup and drop was super convenient."),
                    new DemoReview(5, "Farhan S.", "Original-quality iPhone screen."),
                    new DemoReview(4, "Krupa N.", "Good service, slightly pricey."),
                    new DemoReview(4, "Deep L.", "Laptop hinge fixed properly."))),

        workshop(5, new RepairerProfileRequest(
                "Varachha Cycle Works (Demo)",
                "Bicycle servicing and repairs for kids' cycles, geared bikes and e-cycles. Doorstep pickup for full services.",
                Set.of(BICYCLE), List.of("Geared cycles", "E-cycles", "Wheel truing"),
                List.of(svc("Puncture repair", BICYCLE, FIXED, "80", 1),
                        svc("Full service and tuning", BICYCLE, FIXED, "450", 1),
                        svc("Gear and brake overhaul", BICYCLE, STARTING_FROM, "650", 2)),
                "+919800000106", "Near Hirabaug Circle, Varachha Road", "Surat", "395006",
                21.2040, 72.8590, with(monToSat("08:00", "20:00"), day(DayOfWeek.SUNDAY, "08:00", "13:00")),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.PICKUP_DROPOFF), AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.UNVERIFIED,
            List.of(new DemoReview(4, "Aarav P.", "My son's cycle rides like new."),
                    new DemoReview(5, "Hetal M.", "Quick puncture fix, honest pricing."))),

        workshop(6, new RepairerProfileRequest(
                "Alkapuri TV & Electronics (Demo)",
                "LED and smart TV repairs, inverters, UPS and microwaves. Free pickup for TVs above 40 inches within Vadodara.",
                Set.of(TV, ELECTRICAL, HOME_APPLIANCE), List.of("Smart TVs", "Inverters and UPS", "Microwaves"),
                List.of(svc("LED TV repair", TV, STARTING_FROM, "1500", 3),
                        svc("Inverter and UPS repair", ELECTRICAL, STARTING_FROM, "700", 2),
                        svc("Microwave repair", HOME_APPLIANCE, STARTING_FROM, "650", 2)),
                "+919800000107", "R C Dutt Road, Alkapuri", "Vadodara", "390007",
                22.3105, 73.1690, monToSat("10:00", "20:00"),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.PICKUP_DROPOFF), AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.VERIFIED,
            List.of(new DemoReview(4, "Chirag H.", "Inverter repaired same day."),
                    new DemoReview(4, "Neha V.", "TV picked up and returned in 3 days."),
                    new DemoReview(5, "Sanjay R.", "Very knowledgeable technician."))),

        workshop(7, new RepairerProfileRequest(
                "Fatehgunj Fix Point (Demo)",
                "Mail-in repairs for phone back glass, smartwatches and Bluetooth speakers. Pack it, send it, we fix and return it.",
                Set.of(MOBILE, OTHER), List.of("Back glass", "Smartwatches", "Bluetooth speakers"),
                List.of(svc("Phone back glass replacement", MOBILE, STARTING_FROM, "999", 3),
                        svc("Smartwatch battery replacement", OTHER, STARTING_FROM, "599", 4),
                        svc("Bluetooth speaker repair", OTHER, STARTING_FROM, "449", 4)),
                "+919800000108", "Opp. Fatehgunj Post Office, Fatehgunj", "Vadodara", "390002",
                22.3235, 73.1846, monToFri("11:00", "18:00"),
                Set.of(RepairMethod.SEND_ITEM), AvailabilityStatus.UNAVAILABLE,
                "Temporarily on leave; mail-in repairs resume soon"),
            VerificationStatus.UNVERIFIED,
            List.of(new DemoReview(3, "Tanvi S.", "Repair was fine, return shipping took long."),
                    new DemoReview(4, "Kabir J.", "Speaker works again."))),

        workshop(8, new RepairerProfileRequest(
                "Kalawad Road Mobile Hub (Demo)",
                "Mobile, tablet and laptop repairs and upgrades in Rajkot. Walk in, courier your device, or book a pickup.",
                Set.of(MOBILE, TABLET, LAPTOP), List.of("Software flashing", "SSD upgrades", "Charging issues"),
                List.of(svc("Phone screen replacement", MOBILE, STARTING_FROM, "1099", 1),
                        svc("Software flashing", MOBILE, FIXED, "399", 1),
                        svc("Tablet charging port repair", TABLET, FIXED, "699", 2),
                        svc("Laptop RAM or SSD upgrade (labour)", LAPTOP, STARTING_FROM, "499", 1)),
                "+919800000109", "Kalawad Road, near KKV Chowk", "Rajkot", "360005",
                22.2840, 70.7705, with(monToSat("10:00", "21:00"), day(DayOfWeek.SUNDAY, "10:00", "14:00")),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.SEND_ITEM, RepairMethod.PICKUP_DROPOFF),
                AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.UNVERIFIED,
            List.of(new DemoReview(5, "Bhavik T.", "SSD upgrade made my old laptop fast."),
                    new DemoReview(4, "Riya K.", "Phone screen fixed in an hour."),
                    new DemoReview(4, "Manoj D.", "Good, transparent pricing."))),

        workshop(9, new RepairerProfileRequest(
                "Waghawadi Electronics Service (Demo)",
                "Air conditioners, fans and TVs serviced in Bhavnagar. Newly listed: no ratings yet.",
                Set.of(HOME_APPLIANCE, TV, ELECTRICAL), List.of("AC servicing", "Ceiling fans", "TV repair"),
                List.of(svc("AC service and gas top-up", HOME_APPLIANCE, STARTING_FROM, "1200", 1),
                        svc("Ceiling fan repair", ELECTRICAL, FIXED, "300", 1),
                        svc("TV repair", TV, STARTING_FROM, "1200", 3)),
                "+919800000110", "Waghawadi Road, near Gandhi Smruti", "Bhavnagar", "364002",
                21.7600, 72.1480, monToSat("09:30", "19:30"),
                Set.of(RepairMethod.IN_PERSON, RepairMethod.PICKUP_DROPOFF), AvailabilityStatus.AVAILABLE, null),
            VerificationStatus.UNVERIFIED,
            List.of()));

    private DemoRepairers() {
    }

    private static DemoWorkshop workshop(int index, RepairerProfileRequest profile, VerificationStatus verification,
                                         List<DemoReview> reviews) {
        return new DemoWorkshop(FIRST_ACCOUNT_ID - index, profile, verification, reviews);
    }

    private static ServiceOfferingRequest svc(String name, RepairServiceCategory category, PriceType type,
                                              String rupees, int days) {
        return new ServiceOfferingRequest(null, name, null, category, type, new BigDecimal(rupees), days);
    }

    private static WorkingHoursRequest day(DayOfWeek day, String opens, String closes) {
        return new WorkingHoursRequest(day, opens, closes);
    }

    private static List<WorkingHoursRequest> range(DayOfWeek from, DayOfWeek to, String opens, String closes) {
        List<WorkingHoursRequest> hours = new ArrayList<>();
        for (int d = from.getValue(); d <= to.getValue(); d++) {
            hours.add(day(DayOfWeek.of(d), opens, closes));
        }
        return hours;
    }

    private static List<WorkingHoursRequest> monToFri(String opens, String closes) {
        return range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, opens, closes);
    }

    private static List<WorkingHoursRequest> monToSat(String opens, String closes) {
        return range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, opens, closes);
    }

    private static List<WorkingHoursRequest> daily(String opens, String closes) {
        return range(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, opens, closes);
    }

    private static List<WorkingHoursRequest> with(List<WorkingHoursRequest> base, WorkingHoursRequest extra) {
        List<WorkingHoursRequest> hours = new ArrayList<>(base);
        hours.add(extra);
        return hours;
    }
}
