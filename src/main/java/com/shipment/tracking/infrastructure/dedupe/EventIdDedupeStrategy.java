package com.shipment.tracking.infrastructure.dedupe;

import com.shipment.tracking.application.command.IngestShipmentEventCommand;
import com.shipment.tracking.application.dedupe.DedupeStrategy;
import com.shipment.tracking.infrastructure.persistence.entity.ShipmentEventEntity;
import com.shipment.tracking.infrastructure.persistence.mapper.ShipmentPersistenceMapper;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentEventRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Phase 1 / {@code dhl} dedupe: {@code (partner, event_id)} (docs/ANALYSIS.md §7.1).
 */
@Component
public class EventIdDedupeStrategy implements DedupeStrategy {

    private final ShipmentEventRepository eventRepository;

    public EventIdDedupeStrategy(ShipmentEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public boolean requiresEventId() {
        return true;
    }

    @Override
    public boolean isDuplicate(IngestShipmentEventCommand command) {
        return eventRepository.existsByPartnerAndEventId(command.partner(), command.eventId());
    }

    @Override
    public Optional<String> findCanonicalPayloadHash(IngestShipmentEventCommand command) {
        return eventRepository
                .findFirstByPartnerAndEventIdOrderByIdAsc(command.partner(), command.eventId())
                .map(ShipmentEventEntity::getPayloadHash);
    }

    @Override
    public String storageEventIdForInsert(IngestShipmentEventCommand command, boolean duplicate) {
        if (duplicate) {
            return ShipmentPersistenceMapper.duplicateStorageEventId(command.eventId());
        }
        return command.eventId();
    }
}
