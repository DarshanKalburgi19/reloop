package com.reloop.repair.dto.repairer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.DayOfWeek;

/** Opening hours for one day, e.g. {@code {"day":"MONDAY","opens":"10:00","closes":"19:00"}}. Omitted days are closed. */
public record WorkingHoursRequest(
        @NotNull DayOfWeek day,
        @NotNull @Pattern(regexp = RepairerValidation.TIME, message = "must be a 24-hour time HH:mm") String opens,
        @NotNull @Pattern(regexp = RepairerValidation.TIME, message = "must be a 24-hour time HH:mm") String closes) {
}
