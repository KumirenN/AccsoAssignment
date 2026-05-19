package com.accso.shipment.application.result;

import com.accso.shipment.api.dto.IngestShipmentEventResponse;

/**
 * Internal outcome of ingest. {@link com.accso.shipment.api.ShipmentEventController} maps
 * {@link #invalidStatus()} to HTTP 400 (docs/ANALYSIS.md §7.4); duplicates use HTTP 200 (§7.1).
 */
public record IngestResult(IngestShipmentEventResponse response, boolean invalidStatus) {

    /**
     * Successful ingest (accepted or duplicate) with a partner-facing response body.
     */
    public static IngestResult success(IngestShipmentEventResponse response) {
        return new IngestResult(response, false);
    }

    /**
     * Invalid status enum — no response body; controller returns 400 (§7.4).
     */
    public static IngestResult forInvalidStatus() {
        return new IngestResult(null, true);
    }
}
