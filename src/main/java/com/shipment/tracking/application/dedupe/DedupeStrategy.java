package com.shipment.tracking.application.dedupe;

import com.shipment.tracking.application.command.IngestShipmentEventCommand;
import java.util.Optional;

/**
 * Partner-specific duplicate detection (docs/ANALYSIS.md §6.1, §7.1).
 *
 * <p>Phase 2: {@code event-id} ({@code dhl}) vs {@code natural-key} ({@code acme}).
 */
public interface DedupeStrategy {

    /** Whether inbound {@code eventId} is required for this partner. */
    boolean requiresEventId();

    /** True when this ingest matches an existing logical update (§7.1). */
    boolean isDuplicate(IngestShipmentEventCommand command);

    /** Payload hash of the first stored row for this dedupe key, if any. */
    Optional<String> findCanonicalPayloadHash(IngestShipmentEventCommand command);

    /**
     * Value to persist in {@code shipment_event.event_id} for this insert.
     *
     * @param duplicate when true, must not collide with an existing UK row
     */
    String storageEventIdForInsert(IngestShipmentEventCommand command, boolean duplicate);
}
