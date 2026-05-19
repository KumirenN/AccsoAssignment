package com.accso.shipment.domain.model;

import java.time.Instant;

/**
 * Courier event in domain terms (decoupled from JPA). {@code id} is null before persistence.
 * Timestamps: {@code occurredAt} drives projection order (§7.8); {@code receivedAt} is tie-breaker.
 */
public record DomainEvent(
        Long id,
        String partner,
        String eventId,
        String shipmentId,
        ShipmentStatus status,
        Instant occurredAt,
        Instant receivedAt,
        String location) {

    /**
     * Factory for a not-yet-persisted event during ingest projection.
     */
    public static DomainEvent withoutId(
            String partner,
            String eventId,
            String shipmentId,
            ShipmentStatus status,
            Instant occurredAt,
            Instant receivedAt,
            String location) {
        return new DomainEvent(null, partner, eventId, shipmentId, status, occurredAt, receivedAt, location);
    }
}
