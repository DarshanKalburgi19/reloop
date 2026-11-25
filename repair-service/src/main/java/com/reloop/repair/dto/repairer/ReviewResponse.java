package com.reloop.repair.dto.repairer;

import java.time.LocalDateTime;

/** A rating as shown on a workshop profile (reviewer display name only). */
public record ReviewResponse(int rating, String comment, String reviewerName, LocalDateTime createdAt, boolean demo) {
}
