package com.shipment.tracking.infrastructure.dedupe;

import com.shipment.tracking.application.command.IngestShipmentEventCommand;
import com.shipment.tracking.application.dedupe.DedupeStrategy;
import com.shipment.tracking.domain.model.Disposition;
import com.shipment.tracking.infrastructure.persistence.entity.ShipmentEventEntity;
import com.shipment.tracking.infrastructure.persistence.mapper.ShipmentPersistenceMapper;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentEventRepository;
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
        return eventRepository.existsByPartnerAndShipmentIdAndStatusAndOccurredAtAndDispositionIsNot(
                command.partner(),
                command.shipmentId(),
                command.status(),
                command.occurredAt(),
                Disposition.DUPLICATE.name());
    }

    @Override
    public Optional<String> findCanonicalPayloadHash(IngestShipmentEventCommand command) {
        return eventRepository
                .findFirstByPartnerAndShipmentIdAndStatusAndOccurredAtAndDispositionIsNotOrderByIdAsc(
                        command.partner(),
                        command.shipmentId(),
                        command.status(),
                        command.occurredAt(),
                        Disposition.DUPLICATE.name())
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
