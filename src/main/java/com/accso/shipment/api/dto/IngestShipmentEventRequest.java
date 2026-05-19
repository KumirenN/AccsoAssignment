package com.accso.shipment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * JSON body for {@code POST /shipment-events} (docs/ANALYSIS.md §8).
 *
 * <p>{@code eventId} required for event-id partners ({@code dhl}); optional for natural-key
 * partners ({@code acme}) — see {@code application.yml} (docs/ANALYSIS.md §6.1). {@code occurredAt}
 * drives projection; {@code receivedAt} is audit/tie-break only (§7.8).
 */
public class IngestShipmentEventRequest {

    /** Partner-supplied id; required when partner uses event-id dedupe (§7.1). */
    private String eventId;

    @NotBlank
    private String partner;

    @NotBlank
    private String shipmentId;

    /** Must match a {@link com.accso.shipment.domain.model.ShipmentStatus} name or ingest is §7.4 invalid. */
    @NotBlank
    private String status;

    @NotNull
    private Instant occurredAt;

    @NotNull
    private Instant receivedAt;

    private String location;

    /** Returns the partner event id. */
    public String getEventId() {
        return eventId;
    }

    /** Sets the partner event id. */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /** Returns the courier partner code. */
    public String getPartner() {
        return partner;
    }

    /** Sets the courier partner code. */
    public void setPartner(String partner) {
        this.partner = partner;
    }

    /** Returns the shipment identifier. */
    public String getShipmentId() {
        return shipmentId;
    }

    /** Sets the shipment identifier. */
    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    /** Returns the status string from the webhook. */
    public String getStatus() {
        return status;
    }

    /** Sets the status string from the webhook. */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Returns when the event occurred in the real world (projection ordering). */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** Sets when the event occurred in the real world. */
    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    /** Returns when the partner sent/received the webhook (audit tie-break). */
    public Instant getReceivedAt() {
        return receivedAt;
    }

    /** Sets when the partner sent/received the webhook. */
    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    /** Returns optional location text. */
    public String getLocation() {
        return location;
    }

    /** Sets optional location text. */
    public void setLocation(String location) {
        this.location = location;
    }
}
