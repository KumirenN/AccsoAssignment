package com.shipment.tracking.domain.model;

import java.util.Comparator;
import java.util.Optional;

/**
 * Courier lifecycle statuses (docs/ANALYSIS.md §2). {@link #ordinalRank} resolves same-instant
 * conflicts — higher rank wins (DELIVERY_EXCEPTION highest).
 */
public enum ShipmentStatus {
    LABEL_CREATED(1),
    HANDED_TO_CARRIER(2),
    IN_TRANSIT(3),
    OUT_FOR_DELIVERY(4),
    DELIVERED(5),
    RETURNED(6),
    DELIVERY_EXCEPTION(7);

    private final int ordinalRank;

    ShipmentStatus(int ordinalRank) {
        this.ordinalRank = ordinalRank;
    }

    /**
     * Numeric order used for forward-only moves and same-time conflict resolution (§7.2, §7.3).
     */
    public int getOrdinalRank() {
        return ordinalRank;
    }

    /**
     * Parses webhook status string; empty for unknown values (triggers §7.4 invalid path).
     */
    public static Optional<ShipmentStatus> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ShipmentStatus.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * True if this status is strictly later in the lifecycle than {@code current} (§7.2).
     */
    public boolean isForwardFrom(ShipmentStatus current) {
        return this.ordinalRank > current.ordinalRank;
    }

    /**
     * Comparator for sorting by lifecycle rank.
     */
    public static Comparator<ShipmentStatus> byRank() {
        return Comparator.comparingInt(ShipmentStatus::getOrdinalRank);
    }
}
