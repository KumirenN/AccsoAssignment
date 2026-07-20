package com.shipment.tracking.application;

import com.shipment.tracking.api.dto.ShipmentEventHistoryResponse;
import com.shipment.tracking.api.dto.ShipmentEventItem;
import com.shipment.tracking.infrastructure.persistence.entity.ShipmentEventEntity;
import com.shipment.tracking.infrastructure.persistence.mapper.ShipmentPersistenceMapper;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentEventRepository;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for {@code GET /shipments/{id}/events} — full audit timeline.
 *
 * <p>See docs/ANALYSIS.md §7.8 (history ordering) and §8 (API summary). Every ingest attempt is
 * returned, including {@code DUPLICATE} and {@code REJECTED_INVALID} rows (§7.6).
 */
@Service
public class GetShipmentHistoryService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository eventRepository;

    /**
     * Creates the service with shipment and event repositories.
     */
    public GetShipmentHistoryService(
            ShipmentRepository shipmentRepository, ShipmentEventRepository eventRepository) {
        this.shipmentRepository = shipmentRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Loads all audit rows for a shipment in chronological order.
     *
     * <p>Ordering: {@code occurredAt} ASC, then {@code receivedAt} ASC, then {@code id} ASC
     * (docs/ANALYSIS.md §7.8). Throws {@link ShipmentNotFoundException} when the id is unknown to
     * both tables — see {@link #isKnownShipment(String)}.
     */
    @Transactional(readOnly = true)
    public ShipmentEventHistoryResponse getEvents(String shipmentId) {
        if (!isKnownShipment(shipmentId)) {
            throw new ShipmentNotFoundException(shipmentId);
        }

        List<ShipmentEventEntity> rows =
                eventRepository.findByShipmentIdOrderByOccurredAtAscReceivedAtAscIdAsc(shipmentId);

        ShipmentEventHistoryResponse response = new ShipmentEventHistoryResponse();
        response.setShipmentId(shipmentId);
        response.setEvents(rows.stream().map(this::toItem).toList());
        return response;
    }

    /**
     * Returns whether this shipment id is known to the service.
     *
     * <p>This is <strong>not</strong> the same column checked twice — it is two different tables:
     * <ul>
     *   <li>{@code shipment} — projected current state (PK {@code shipment_id}). Created on first
     *       <em>accepted</em> ingest.</li>
     *   <li>{@code shipment_event} — immutable audit log (column {@code shipment_id} on each row).
     *       Always written on ingest, including invalid status (docs/ANALYSIS.md §7.4).</li>
     * </ul>
     *
     * <p>Example: only {@code REJECTED_INVALID} events were posted for {@code ship-bad-001} → no
     * {@code shipment} row, but {@code shipment_event} rows exist. History GET must return 200;
     * current-state GET correctly returns 404 ({@link GetShipmentService}).
     */
    private boolean isKnownShipment(String shipmentId) {
        return shipmentRepository.existsById(shipmentId) || eventRepository.existsByShipmentId(shipmentId);
    }

    /**
     * Maps a persisted audit entity to the API item, exposing the partner's logical {@code eventId}
     * (strips internal {@code ::dup::} suffix — docs/ANALYSIS.md §7.1, ADR 002).
     */
    private ShipmentEventItem toItem(ShipmentEventEntity entity) {
        ShipmentEventItem item = new ShipmentEventItem();
        item.setId(entity.getId());
        item.setPartner(entity.getPartner());
        item.setEventId(ShipmentPersistenceMapper.logicalEventId(entity.getEventId()));
        item.setShipmentId(entity.getShipmentId());
        item.setStatus(entity.getStatus());
        item.setOccurredAt(entity.getOccurredAt());
        item.setReceivedAt(entity.getReceivedAt());
        item.setLocation(entity.getLocation());
        item.setDisposition(entity.getDisposition());
        item.setStateChanged(entity.isStateChanged());
        item.setIngestedAt(entity.getIngestedAt());
        return item;
    }
}
