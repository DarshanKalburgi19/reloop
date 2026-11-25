package com.reloop.repair.dto.repairer;

/** Star rating summary. {@code average} (1.0-5.0, one decimal) is null when there are no ratings yet. */
public record RatingSummary(Double average, int count) {
}
