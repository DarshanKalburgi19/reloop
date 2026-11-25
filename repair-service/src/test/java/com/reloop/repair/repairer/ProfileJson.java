package com.reloop.repair.repairer;

/** Request bodies for {@code POST/PUT /api/repairers}. */
final class ProfileJson {

    private ProfileJson() {
    }

    /** A complete, valid Ahmedabad workshop. */
    static String valid(String name) {
        return withServices(name, """
            [{"name":"Phone screen replacement","category":"MOBILE","priceType":"STARTING_FROM","price":1499,"estimatedDays":1},
             {"name":"Battery replacement","category":"MOBILE","priceType":"FIXED","price":"899.50","estimatedDays":1},
             {"name":"Laptop OS reinstall","category":"LAPTOP","priceType":"FIXED","price":499}]""");
    }

    static String withServices(String name, String servicesJson) {
        return """
            {"businessName":"%s",
             "description":"Phone and laptop repairs near CG Road with genuine parts and warranty.",
             "categories":["MOBILE","LAPTOP"],
             "expertise":["Screen replacement","Micro-soldering"],
             "services":%s,
             "phone":"+919812345678",
             "addressLine":"12 CG Road, Navrangpura",
             "city":"ahmedabad",
             "pincode":"380009",
             "latitude":23.0276,
             "longitude":72.5577,
             "workingHours":[
               {"day":"MONDAY","opens":"10:00","closes":"19:00"},{"day":"TUESDAY","opens":"10:00","closes":"19:00"},
               {"day":"WEDNESDAY","opens":"10:00","closes":"19:00"},{"day":"THURSDAY","opens":"10:00","closes":"19:00"},
               {"day":"FRIDAY","opens":"10:00","closes":"19:00"},{"day":"SATURDAY","opens":"10:00","closes":"19:00"}],
             "repairMethods":["IN_PERSON","SEND_ITEM"],
             "availability":"AVAILABLE",
             "availabilityNote":"Same-day screen repairs"}
            """.formatted(name, servicesJson);
    }

    /** Replaces one top-level JSON fragment, e.g. {@code "latitude":23.0276} -> {@code "latitude":72.5}. */
    static String replace(String json, String from, String to) {
        if (!json.contains(from)) {
            throw new IllegalArgumentException("fragment not found: " + from);
        }
        return json.replace(from, to);
    }
}
