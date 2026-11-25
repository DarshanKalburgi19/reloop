package com.reloop.repair.service;

import com.reloop.repair.entity.WorkingHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns structured hours into a short human summary, grouping consecutive days with the same hours:
 * {@code "Mon-Sat 10:00-19:00, Sun closed"} or {@code "Daily 09:00-21:00"}.
 */
public final class WorkingHoursFormatter {

    public static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String CLOSED = "closed";

    private WorkingHoursFormatter() {
    }

    /** @return the summary, or null when no hours are set */
    public static String summary(List<WorkingHours> hours) {
        if (hours == null || hours.isEmpty()) {
            return null;
        }
        Map<DayOfWeek, String> byDay = new EnumMap<>(DayOfWeek.class);
        for (WorkingHours h : hours) {
            byDay.put(h.getDayOfWeek(), range(h.getOpensAt(), h.getClosesAt()));
        }
        String[] value = new String[7];
        for (DayOfWeek day : DayOfWeek.values()) {
            value[day.ordinal()] = byDay.getOrDefault(day, CLOSED);
        }
        if (!CLOSED.equals(value[0]) && allSame(value)) {
            return "Daily " + value[0];
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= 7; i++) {
            if (i == 7 || !value[i].equals(value[start])) {
                String days = start == i - 1 ? SHORT[start] : SHORT[start] + "-" + SHORT[i - 1];
                parts.add(days + " " + value[start]);
                start = i;
            }
        }
        return String.join(", ", parts);
    }

    public static String format(LocalTime time) {
        return time.format(HH_MM);
    }

    private static String range(LocalTime opens, LocalTime closes) {
        return format(opens) + "-" + format(closes);
    }

    private static boolean allSame(String[] values) {
        for (String v : values) {
            if (!v.equals(values[0])) {
                return false;
            }
        }
        return true;
    }
}
