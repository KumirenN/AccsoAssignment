package com.accso.shipment.infrastructure.persistence.mapper;

import com.accso.shipment.application.command.IngestShipmentEventCommand;
import com.accso.shipment.domain.model.Disposition;
import com.accso.shipment.domain.model.DomainEvent;
import com.accso.shipment.domain.model.ShipmentSnapshot;
import com.accso.shipment.domain.model.ShipmentStatus;
import com.accso.shipment.infrastructure.persistence.entity.ShipmentEntity;
import com.accso.shipment.infrastructure.persistence.entity.ShipmentEventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps between JPA entities and domain types. Keeps SQL/JPA details out of application services.
 */
@Component
public class ShipmentPersistenceMapper {

    private static final String DUPLICATE_EVENT_ID_SUFFIX = "::dup::";

    /**
     * Converts a persisted audit row to a {@link DomainEvent} for projection.
     */
    public DomainEvent toDomainEvent(ShipmentEventEntity entity) {
        ShipmentStatus status =
                ShipmentStatus.fromString(entity.getStatus()).orElse(ShipmentStatus.LABEL_CREATED);
        return new DomainEvent(
                entity.getId(),
                entity.getPartner(),
                logicalEventId(entity.getEventId()),
                entity.getShipmentId(),
                status,
                entity.getOccurredAt(),
                entity.getReceivedAt(),
                entity.getLocation());
    }

    /**
     * Filters to dispositions that affect projected status (docs/ANALYSIS.md §7.6 — excludes
     * DUPLICATE and REJECTED_INVALID).
     */
    public List<DomainEvent> toAcceptedDomainEvents(List<ShipmentEventEntity> entities) {
        return entities.stream()
                .filter(e -> Disposition.valueOf(e.getDisposition()).countsAsProcessed())
                .map(this::toDomainEvent)
                .toList();
    }

    /**
     * Builds a {@link ShipmentSnapshot} from the current {@code shipment} row before projection.
     */
    public ShipmentSnapshot toSnapshot(ShipmentEntity entity) {
        ShipmentStatus status = ShipmentStatus.fromString(entity.getCurrentStatus()).orElse(null);
        return new ShipmentSnapshot(
                entity.getShipmentId(),
                status,
                entity.getStatusOccurredAt(),
                entity.getLocation(),
                entity.getLatestDeliveredAt(),
                entity.getProcessedEventCount());
    }

    /**
     * Writes projected fields back to {@code shipment} after an accepted ingest
     * (docs/ANALYSIS.md §7.5, §7.7).
     */
    public void applySnapshot(
            ShipmentEntity entity, ShipmentSnapshot snapshot, String explanation, int processedCount) {
        entity.setCurrentStatus(snapshot.currentStatus().name());
        entity.setStatusOccurredAt(snapshot.statusOccurredAt());
        entity.setLocation(snapshot.location());
        entity.setLatestDeliveredAt(snapshot.latestDeliveredAt());
        entity.setStateExplanation(explanation);
        entity.setProcessedEventCount(processedCount);
    }

    /**
     * Builds a new {@code shipment_event} entity for insert (immutable audit log).
     *
     * @param storedEventId partner id, or synthetic id for duplicates (§7.1, ADR 002)
     */
    public ShipmentEventEntity toNewEventEntity(
            IngestShipmentEventCommand command,
            String storedEventId,
            Disposition disposition,
            boolean stateChanged,
            String payloadHash,
            Instant ingestedAt) {
        ShipmentEventEntity entity = new ShipmentEventEntity();
        entity.setShipmentId(command.shipmentId());
        entity.setPartner(command.partner());
        entity.setEventId(storedEventId);
        entity.setStatus(command.status());
        entity.setOccurredAt(command.occurredAt());
        entity.setReceivedAt(command.receivedAt());
        entity.setLocation(command.location());
        entity.setDisposition(disposition.name());
        entity.setStateChanged(stateChanged);
        entity.setRawPayload(command.rawPayload());
        entity.setPayloadHash(payloadHash);
        entity.setIngestedAt(ingestedAt);
        return entity;
    }

    /**
     * Returns the partner-visible event id, stripping the internal duplicate suffix (ADR 002).
     */
    public static String logicalEventId(String storedEventId) {
        if (storedEventId == null) {
            return null;
        }
        int dup = storedEventId.indexOf(DUPLICATE_EVENT_ID_SUFFIX);
        return dup >= 0 ? storedEventId.substring(0, dup) : storedEventId;
    }

    /**
     * Generates a unique DB {@code event_id} for duplicate rows while preserving logical id in API
     * (UK on partner + event_id — docs/ANALYSIS.md §7.1).
     */
    public static String duplicateStorageEventId(String partnerEventId) {
        return partnerEventId + DUPLICATE_EVENT_ID_SUFFIX + System.nanoTime();
    }
}
