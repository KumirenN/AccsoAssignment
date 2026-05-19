package com.accso.shipment.infrastructure.dedupe;

import com.accso.shipment.application.command.IngestShipmentEventCommand;
import com.accso.shipment.application.dedupe.DedupeStrategy;
import com.accso.shipment.infrastructure.persistence.entity.ShipmentEventEntity;
import com.accso.shipment.infrastructure.persistence.mapper.ShipmentPersistenceMapper;
import com.accso.shipment.infrastructure.persistence.repository.ShipmentEventRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Phase 2 / {@code acme} dedupe: {@code (partner, shipment_id, status, occurred_at)} — ignores
 * {@code receivedAt} (docs/ANALYSIS.md §6.1).
 */
@Component
public class NaturalKeyDedupeStrategy implements DedupeStrategy {

    private final ShipmentEventRepository eventRepository;

    public NaturalKeyDedupeStrategy(ShipmentEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public boolean requiresEventId() {
        return false;
    }

    @Override
    public boolean isDuplicate(IngestShipmentEventCommand command) {
        return eventRepository.existsByPartnerAndShipmentIdAndStatusAndOccurredAt(
                command.partner(), command.shipmentId(), command.status(), command.occurredAt());
    }

    @Override
    public Optional<String> findCanonicalPayloadHash(IngestShipmentEventCommand command) {
        return eventRepository
                .findFirstByPartnerAndShipmentIdAndStatusAndOccurredAtOrderByIdAsc(
                        command.partner(), command.shipmentId(), command.status(), command.occurredAt())
                .map(ShipmentEventEntity::getPayloadHash);
    }

    @Override
    public String storageEventIdForInsert(IngestShipmentEventCommand command, boolean duplicate) {
        if (duplicate) {
            return ShipmentPersistenceMapper.duplicateStorageEventId(naturalKeyToken(command));
        }
        return null;
    }

    /** Stable token for synthetic duplicate {@code event_id} rows (not the dedupe UK itself). */
    static String naturalKeyToken(IngestShipmentEventCommand command) {
        return "nk::"
                + command.partner()
                + "::"
                + command.shipmentId()
                + "::"
                + command.status()
                + "::"
                + command.occurredAt();
    }
}
