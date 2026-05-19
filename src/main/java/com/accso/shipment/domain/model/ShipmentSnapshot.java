package com.accso.shipment.domain.model;

import java.time.Instant;

/**
 * In-memory shipment state during projection; mirrors {@code shipment} table fields.
 * {@code latestDeliveredAt} supports RETURNED-after-delivery (docs/ANALYSIS.md §3, §7).
 */
public record ShipmentSnapshot(
        String shipmentId,
        ShipmentStatus currentStatus,
        Instant statusOccurredAt,
        String location,
        Instant latestDeliveredAt,
        int processedEventCount) {

    /**
     * Starting point before any accepted event has been applied.
     */
    public static ShipmentSnapshot empty(String shipmentId) {
        return new ShipmentSnapshot(shipmentId, null, null, null, null, 0);
    }

    /**
     * Whether a current status has been established from accepted events.
     */
    public boolean hasStatus() {
        return currentStatus != null;
    }
}
