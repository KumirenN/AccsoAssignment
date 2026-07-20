package com.shipment.tracking.infrastructure.persistence.repository;

import com.shipment.tracking.infrastructure.persistence.entity.ShipmentEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the {@code shipment_event} audit table (docs/ANALYSIS.md §7.8, DATABASE_ERD).
 */
public interface ShipmentEventRepository extends JpaRepository<ShipmentEventEntity, Long> {

    /**
     * Timeline query for GET history — order matches docs/ANALYSIS.md §7.8 and index
     * {@code idx_event_shipment_timeline}.
     */
    List<ShipmentEventEntity> findByShipmentIdOrderByOccurredAtAscReceivedAtAscIdAsc(String shipmentId);

    /**
     * Phase 1 duplicate detection on {@code (partner, event_id)} (docs/ANALYSIS.md §7.1).
     */
    boolean existsByPartnerAndEventId(String partner, String eventId);

    /**
     * First canonical row for a partner event id (payload hash comparison on duplicates).
     */
    Optional<ShipmentEventEntity> findFirstByPartnerAndEventIdOrderByIdAsc(String partner, String eventId);

    /**
     * Counts rows that contribute to {@code processedEventCount} (docs/ANALYSIS.md §7.5).
     */
    long countByShipmentIdAndDispositionIn(String shipmentId, List<String> dispositions);

    /**
     * True when any audit row exists for this shipment id (e.g. invalid-only ingests — §7.4).
     */
    boolean existsByShipmentId(String shipmentId);

    /**
     * Phase 2 natural-key duplicate detection — same logical update already accepted (not a prior
     * DUPLICATE audit row). {@code receivedAt} ignored for dedupe (docs/ANALYSIS.md §6.1).
     */
    boolean existsByPartnerAndShipmentIdAndStatusAndOccurredAtAndDispositionIsNot(
            String partner,
            String shipmentId,
            String status,
            java.time.Instant occurredAt,
            String disposition);

    /**
     * First canonical (non-DUPLICATE) row for natural-key payload hash comparison.
     */
    Optional<ShipmentEventEntity>
            findFirstByPartnerAndShipmentIdAndStatusAndOccurredAtAndDispositionIsNotOrderByIdAsc(
                    String partner,
                    String shipmentId,
                    String status,
                    java.time.Instant occurredAt,
                    String disposition);
}
