package com.shipment.tracking.domain.model;

import java.util.List;

/**
 * How an ingest was handled internally (docs/ANALYSIS.md §7.6). Exposed on GET history, not on POST
 * to partners.
 */
public enum Disposition {
    ACCEPTED,
    ACCEPTED_STATE_CHANGED,
    ACCEPTED_NO_STATE_CHANGE,
    DUPLICATE,
    REJECTED_INVALID;

    /**
     * Whether this row counts toward {@code processedEventCount} (docs/ANALYSIS.md §7.5).
     */
    public boolean countsAsProcessed() {
        return this == ACCEPTED || this == ACCEPTED_STATE_CHANGED || this == ACCEPTED_NO_STATE_CHANGE;
    }

    /**
     * String names for JPA {@code countByShipmentIdAndDispositionIn} (§7.5).
     */
    public static List<String> processedDispositionNames() {
        return List.of(
                ACCEPTED.name(), ACCEPTED_STATE_CHANGED.name(), ACCEPTED_NO_STATE_CHANGE.name());
    }
}
