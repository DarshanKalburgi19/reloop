package com.reloop.repair.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reloop.repair.entity.WorkingHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkingHoursFormatterTest {

    private static List<WorkingHours> days(DayOfWeek from, DayOfWeek to, String opens, String closes) {
        List<WorkingHours> hours = new ArrayList<>();
        for (int d = from.getValue(); d <= to.getValue(); d++) {
            hours.add(new WorkingHours(DayOfWeek.of(d), LocalTime.parse(opens), LocalTime.parse(closes)));
        }
        return hours;
    }

    @Test
    void groupsConsecutiveDays() {
        assertThat(WorkingHoursFormatter.summary(days(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, "10:00", "19:00")))
                .isEqualTo("Mon-Sat 10:00-19:00, Sun closed");
        assertThat(WorkingHoursFormatter.summary(days(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, "09:00", "21:00")))
                .isEqualTo("Daily 09:00-21:00");

        List<WorkingHours> mixed = days(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, "10:00", "19:00");
        mixed.add(new WorkingHours(DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));
        assertThat(WorkingHoursFormatter.summary(mixed)).isEqualTo("Mon-Fri 10:00-19:00, Sat 10:00-14:00, Sun closed");
    }

    @Test
    void orderOfInputDoesNotMatterAndEmptyIsNull() {
        List<WorkingHours> reversed = new ArrayList<>(days(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, "11:00", "18:00"));
        java.util.Collections.reverse(reversed);
        assertThat(WorkingHoursFormatter.summary(reversed)).isEqualTo("Mon-Tue closed, Wed-Thu 11:00-18:00, Fri-Sun closed");
        assertThat(WorkingHoursFormatter.summary(List.of())).isNull();
    }
}
