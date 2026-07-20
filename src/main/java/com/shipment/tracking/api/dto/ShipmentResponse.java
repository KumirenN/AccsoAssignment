package com.shipment.tracking.api.dto;

import com.shipment.tracking.infrastructure.persistence.entity.ShipmentEntity;
import java.time.Instant;

/**
 * Response for {@code GET /shipments/{id}} (docs/ANALYSIS.md §7.7, §8).
 */
public class ShipmentResponse {

    private String shipmentId;
    private String currentStatus;
    private Instant statusOccurredAt;
    private int processedEventCount;
    private String stateExplanation;
    private String location;

    /**
     * Maps the persisted projection row to the API DTO.
     */
    public static ShipmentResponse fromEntity(ShipmentEntity entity) {
        ShipmentResponse response = new ShipmentResponse();
        response.setShipmentId(entity.getShipmentId());
        response.setCurrentStatus(entity.getCurrentStatus());
        response.setStatusOccurredAt(entity.getStatusOccurredAt());
        response.setProcessedEventCount(entity.getProcessedEventCount());
        response.setStateExplanation(entity.getStateExplanation());
        response.setLocation(entity.getLocation());
        return response;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    /** Instant of the status in {@link #currentStatus} (from last state-changing accepted event). */
    public Instant getStatusOccurredAt() {
        return statusOccurredAt;
    }

    public void setStatusOccurredAt(Instant statusOccurredAt) {
        this.statusOccurredAt = statusOccurredAt;
    }

    /** Count of accepted dispositions only — docs/ANALYSIS.md §7.5. */
    public int getProcessedEventCount() {
        return processedEventCount;
    }

    public void setProcessedEventCount(int processedEventCount) {
        this.processedEventCount = processedEventCount;
    }

    /** Human-readable reason for current status (§7.7). */
    public String getStateExplanation() {
        return stateExplanation;
    }

    public void setStateExplanation(String stateExplanation) {
        this.stateExplanation = stateExplanation;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
