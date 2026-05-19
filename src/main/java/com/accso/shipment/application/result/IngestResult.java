package com.accso.shipment.application.result;

import com.accso.shipment.api.dto.IngestShipmentEventResponse;

/**
 * Internal outcome of ingest. {@link com.accso.shipment.api.ShipmentEventController} maps
 * {@link #invalidStatus()} and {@link #missingEventId()} to HTTP 400; duplicates use HTTP 200 (§7.1).
 */
public record IngestResult(
        IngestShipmentEventResponse response, boolean invalidStatus, boolean missingEventId) {

    /**
     * Successful ingest (accepted or duplicate) with a partner-facing response body.
     */
    public static IngestResult success(IngestShipmentEventResponse response) {
        return new IngestResult(response, false, false);
    }

    /**
     * Invalid status enum — controller returns 400 (§7.4).
     */
    public static IngestResult forInvalidStatus() {
        return new IngestResult(null, true, false);
    }

    /**
     * Event-id partner sent no {@code eventId} — controller returns 400 (Phase 2, §6.1).
     */
    public static IngestResult forMissingEventId() {
        return new IngestResult(null, false, true);
    }
}
