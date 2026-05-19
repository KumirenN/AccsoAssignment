package com.accso.shipment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for {@code shipment} — projected current state (docs/ANALYSIS.md §7.7, DATABASE_ERD).
 * Not created for invalid-only ingests (§7.4). Standard getters/setters are persistence boilerplate.
 */
@Entity
@Table(name = "shipment")
public class ShipmentEntity {

    @Id
    @Column(name = "shipment_id", length = 64, nullable = false)
    private String shipmentId;

    @Column(name = "current_status", length = 32, nullable = false)
    private String currentStatus;

    @Column(name = "status_occurred_at", nullable = false)
    private Instant statusOccurredAt;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "state_explanation", length = 1024, nullable = false)
    private String stateExplanation;

    @Column(name = "processed_event_count", nullable = false)
    private int processedEventCount;

    @Column(name = "latest_delivered_at")
    private Instant latestDeliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ShipmentEntity() {}

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

    public Instant getStatusOccurredAt() {
        return statusOccurredAt;
    }

    public void setStatusOccurredAt(Instant statusOccurredAt) {
        this.statusOccurredAt = statusOccurredAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStateExplanation() {
        return stateExplanation;
    }

    public void setStateExplanation(String stateExplanation) {
        this.stateExplanation = stateExplanation;
    }

    public int getProcessedEventCount() {
        return processedEventCount;
    }

    public void setProcessedEventCount(int processedEventCount) {
        this.processedEventCount = processedEventCount;
    }

    public Instant getLatestDeliveredAt() {
        return latestDeliveredAt;
    }

    public void setLatestDeliveredAt(Instant latestDeliveredAt) {
        this.latestDeliveredAt = latestDeliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
