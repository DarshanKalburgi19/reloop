package com.reloop.repair.dto.repairer;

import java.time.DayOfWeek;

/** Opening hours for one day ("HH:mm", local time). Days not listed are closed. */
public record WorkingHoursResponse(DayOfWeek day, String opens, String closes) {
}
