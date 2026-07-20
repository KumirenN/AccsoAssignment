package com.shipment.tracking.application;

/**
 * No {@code shipment} row (and, for history GET, no audit rows) for the requested id.
 * Mapped to HTTP 404 (docs/ANALYSIS.md §8).
 */
public class ShipmentNotFoundException extends RuntimeException {

    /**
     * Creates the exception for the given shipment id.
     */
    public ShipmentNotFoundException(String shipmentId) {
        super("Shipment not found: " + shipmentId);
    }
}
