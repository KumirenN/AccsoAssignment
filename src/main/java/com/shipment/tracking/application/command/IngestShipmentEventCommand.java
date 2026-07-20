package com.shipment.tracking.application.command;

import java.time.Instant;

/**
 * Immutable input for {@link com.shipment.tracking.application.IngestShipmentEventService#ingest}.
 * Built from the REST DTO plus serialized JSON for audit (docs/ANALYSIS.md §7.1).
 */
public record IngestShipmentEventCommand(
        String eventId,
        String partner,
        String shipmentId,
        String status,
        Instant occurredAt,
        Instant receivedAt,
        String location,
        String rawPayload) {}
