package com.shipment.tracking.api.dto;

import java.util.List;

/**
 * Response for {@code GET /shipments/{id}/events} — full audit list (docs/ANALYSIS.md §7.8).
 */
public class ShipmentEventHistoryResponse {

    private String shipmentId;
    private List<ShipmentEventItem> events;

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    /** Chronological audit rows including DUPLICATE and REJECTED_INVALID (§7.6). */
    public List<ShipmentEventItem> getEvents() {
        return events;
    }

    public void setEvents(List<ShipmentEventItem> events) {
        this.events = events;
    }
}
