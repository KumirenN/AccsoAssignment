package com.shipment.tracking.api.dto;

import com.shipment.tracking.application.command.IngestShipmentEventCommand;
import com.shipment.tracking.domain.projection.ProjectionResult;

/**
 * Partner-facing ingest outcome (docs/ANALYSIS.md §8). Small flag set — internal
 * {@link com.shipment.tracking.domain.model.Disposition} is not exposed (§7.6).
 */
public class IngestShipmentEventResponse {

    private boolean accepted;
    private boolean duplicate;
    private boolean payloadMismatch;
    private boolean stateChanged;
    private String currentStatus;
    private String shipmentId;
    private String eventId;

    /**
     * Builds response after a successful accepted ingest (not duplicate, not invalid).
     */
    public static IngestShipmentEventResponse forAccepted(
            IngestShipmentEventCommand command, ProjectionResult projection) {
        IngestShipmentEventResponse response = base(command);
        response.setAccepted(true);
        response.setStateChanged(projection.stateChanged());
        response.setCurrentStatus(projection.snapshot().currentStatus().name());
        return response;
    }

    /**
     * Builds response for duplicate webhook — HTTP 200 per §7.1.
     */
    public static IngestShipmentEventResponse forDuplicate(
            IngestShipmentEventCommand command, boolean payloadMismatch, String currentStatusOrNull) {
        IngestShipmentEventResponse response = base(command);
        response.setAccepted(false);
        response.setDuplicate(true);
        response.setPayloadMismatch(payloadMismatch);
        response.setStateChanged(false);
        response.setCurrentStatus(currentStatusOrNull);
        return response;
    }

    /** Shared ids for both accepted and duplicate responses. */
    private static IngestShipmentEventResponse base(IngestShipmentEventCommand command) {
        IngestShipmentEventResponse response = new IngestShipmentEventResponse();
        response.setShipmentId(command.shipmentId());
        response.setEventId(command.eventId());
        return response;
    }

    /** True when the event was accepted into the timeline (not duplicate). */
    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    /** True when partner resent the same {@code (partner, eventId)} (§7.1). */
    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    /** True when duplicate body hash differs from first seen (§7.1). */
    public boolean isPayloadMismatch() {
        return payloadMismatch;
    }

    public void setPayloadMismatch(boolean payloadMismatch) {
        this.payloadMismatch = payloadMismatch;
    }

    /** True when this ingest changed projected current status. */
    public boolean isStateChanged() {
        return stateChanged;
    }

    public void setStateChanged(boolean stateChanged) {
        this.stateChanged = stateChanged;
    }

    /** Current status after ingest (may be unchanged on duplicate). */
    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}
