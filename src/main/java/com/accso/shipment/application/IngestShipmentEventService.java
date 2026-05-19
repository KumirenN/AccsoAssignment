package com.accso.shipment.application;

import com.accso.shipment.api.dto.IngestShipmentEventResponse;
import com.accso.shipment.application.command.IngestShipmentEventCommand;
import com.accso.shipment.application.dedupe.DedupeStrategy;
import com.accso.shipment.application.result.IngestResult;
import com.accso.shipment.domain.model.Disposition;
import com.accso.shipment.domain.model.DomainEvent;
import com.accso.shipment.domain.model.ShipmentSnapshot;
import com.accso.shipment.domain.model.ShipmentStatus;
import com.accso.shipment.domain.projection.ProjectionResult;
import com.accso.shipment.domain.projection.StateProjector;
import com.accso.shipment.infrastructure.dedupe.DedupeStrategyResolver;
import com.accso.shipment.infrastructure.persistence.entity.ShipmentEntity;
import com.accso.shipment.infrastructure.persistence.entity.ShipmentEventEntity;
import com.accso.shipment.infrastructure.persistence.mapper.ShipmentPersistenceMapper;
import com.accso.shipment.infrastructure.persistence.repository.ShipmentEventRepository;
import com.accso.shipment.infrastructure.persistence.repository.ShipmentRepository;
import com.accso.shipment.infrastructure.util.PayloadHasher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Use case for {@code POST /shipment-events} (courier webhook ingest).
 *
 * <p>High-level pipeline — docs/ANALYSIS.md §7:
 * <ol>
 *   <li>§7.4 — invalid status: audit row, HTTP 400, no {@code shipment} update</li>
 *   <li>§7.1 — duplicate (partner-specific key): audit row, HTTP 200, no projection</li>
 *   <li>§7.2–§7.3 — accepted: forward-only projection, update {@code shipment}</li>
 * </ol>
 */
@Service
public class IngestShipmentEventService {

    private static final Logger log = LoggerFactory.getLogger(IngestShipmentEventService.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository eventRepository;
    private final StateProjector stateProjector;
    private final PayloadHasher payloadHasher;
    private final ShipmentPersistenceMapper mapper;
    private final DedupeStrategyResolver dedupeResolver;

    /**
     * Creates the ingest service with repositories, projector, hasher, mapper, and dedupe resolver.
     */
    public IngestShipmentEventService(
            ShipmentRepository shipmentRepository,
            ShipmentEventRepository eventRepository,
            StateProjector stateProjector,
            PayloadHasher payloadHasher,
            ShipmentPersistenceMapper mapper,
            DedupeStrategyResolver dedupeResolver) {
        this.shipmentRepository = shipmentRepository;
        this.eventRepository = eventRepository;
        this.stateProjector = stateProjector;
        this.payloadHasher = payloadHasher;
        this.mapper = mapper;
        this.dedupeResolver = dedupeResolver;
    }

    /**
     * Entry point: validate, dedupe, or accept-and-project. Runs in a single transaction.
     *
     * @return {@link IngestResult} — controller maps validation failures to HTTP 400
     */
    @Transactional
    public IngestResult ingest(IngestShipmentEventCommand command) {
        DedupeStrategy dedupe = dedupeResolver.resolve(command.partner());

        if (dedupe.requiresEventId() && !StringUtils.hasText(command.eventId())) {
            return IngestResult.forMissingEventId();
        }

        IngestAuditContext audit = new IngestAuditContext(
                command, payloadHasher.hash(command.rawPayload()), Instant.now());

        Optional<ShipmentStatus> status = ShipmentStatus.fromString(command.status());
        if (status.isEmpty()) {
            return rejectInvalidStatus(audit, dedupe);
        }
        if (dedupe.isDuplicate(command)) {
            return ingestDuplicate(audit, dedupe);
        }
        return ingestAccepted(audit, status.get(), dedupe);
    }

    /**
     * Persists {@code REJECTED_INVALID} audit row; does not create or update {@code shipment}
     * (docs/ANALYSIS.md §7.4).
     */
    private IngestResult rejectInvalidStatus(IngestAuditContext audit, DedupeStrategy dedupe) {
        String storedEventId = dedupe.storageEventIdForInsert(audit.command(), false);
        saveAuditRow(audit, storedEventId, Disposition.REJECTED_INVALID, false);
        log.info(
                "Ingest rejected invalid status partner={} shipmentId={} eventId={} status={}",
                audit.command().partner(),
                audit.command().shipmentId(),
                audit.command().eventId(),
                audit.command().status());
        return IngestResult.forInvalidStatus();
    }

    /**
     * Handles duplicate webhook: HTTP 200, full audit row, {@code stateChanged=false}
     * (docs/ANALYSIS.md §7.1).
     */
    private IngestResult ingestDuplicate(IngestAuditContext audit, DedupeStrategy dedupe) {
        IngestShipmentEventCommand command = audit.command();
        boolean payloadMismatch = detectPayloadMismatch(audit, dedupe);

        String storageEventId = dedupe.storageEventIdForInsert(command, true);
        saveAuditRow(audit, storageEventId, Disposition.DUPLICATE, false);

        String currentStatus = shipmentRepository
                .findById(command.shipmentId())
                .map(ShipmentEntity::getCurrentStatus)
                .orElse(null);

        return IngestResult.success(
                IngestShipmentEventResponse.forDuplicate(command, payloadMismatch, currentStatus));
    }

    /**
     * Compares SHA-256 of this payload to the first stored row for the same dedupe key (§7.1).
     */
    private boolean detectPayloadMismatch(IngestAuditContext audit, DedupeStrategy dedupe) {
        IngestShipmentEventCommand command = audit.command();
        Optional<String> canonicalHash = dedupe.findCanonicalPayloadHash(command);

        boolean mismatch = canonicalHash.isPresent() && !canonicalHash.get().equals(audit.payloadHash());
        if (mismatch) {
            log.warn(
                    "Payload mismatch on duplicate partner={} shipmentId={} status={}",
                    command.partner(),
                    command.shipmentId(),
                    command.status());
        }
        return mismatch;
    }

    /**
     * Accepted path: load history, project with {@link StateProjector}, persist audit + shipment
     * (docs/ANALYSIS.md §7.2–§7.3, §7.7).
     */
    private IngestResult ingestAccepted(
            IngestAuditContext audit, ShipmentStatus status, DedupeStrategy dedupe) {
        IngestShipmentEventCommand command = audit.command();
        ShipmentEntity shipment = findOrCreateShipment(command.shipmentId(), audit.ingestedAt());

        DomainEvent incoming = toDomainEvent(command, status);
        List<DomainEvent> priorAccepted = loadPriorAcceptedEvents(command.shipmentId());

        ShipmentSnapshot before = mapper.toSnapshot(shipment);
        ProjectionResult projection = stateProjector.projectNewEvent(before, priorAccepted, incoming);

        String storedEventId = dedupe.storageEventIdForInsert(command, false);
        saveAuditRow(audit, storedEventId, projection.disposition(), projection.stateChanged());
        updateShipmentFromProjection(shipment, projection, audit.ingestedAt());

        log.info(
                "Ingest accepted partner={} shipmentId={} eventId={} disposition={} stateChanged={}",
                command.partner(),
                command.shipmentId(),
                command.eventId(),
                projection.disposition(),
                projection.stateChanged());

        return IngestResult.success(IngestShipmentEventResponse.forAccepted(command, projection));
    }

    /**
     * Loads prior events that count toward projection (excludes DUPLICATE / REJECTED_INVALID —
     * docs/ANALYSIS.md §7.6), in timeline order (§7.8).
     */
    private List<DomainEvent> loadPriorAcceptedEvents(String shipmentId) {
        return mapper.toAcceptedDomainEvents(
                eventRepository.findByShipmentIdOrderByOccurredAtAscReceivedAtAscIdAsc(shipmentId));
    }

    /**
     * Builds a domain event from the ingest command (no DB id until persisted).
     */
    private static DomainEvent toDomainEvent(IngestShipmentEventCommand command, ShipmentStatus status) {
        return DomainEvent.withoutId(
                command.partner(),
                command.eventId(),
                command.shipmentId(),
                status,
                command.occurredAt(),
                command.receivedAt(),
                command.location());
    }

    /**
     * Returns existing shipment or creates a stub with {@code LABEL_CREATED} before first accepted
     * status is applied.
     */
    private ShipmentEntity findOrCreateShipment(String shipmentId, Instant now) {
        return shipmentRepository
                .findById(shipmentId)
                .orElseGet(() -> createShipmentStub(shipmentId, now));
    }

    /**
     * Inserts initial {@code shipment} row when the first accepted event arrives for a new id.
     */
    private ShipmentEntity createShipmentStub(String shipmentId, Instant now) {
        ShipmentEntity entity = new ShipmentEntity();
        entity.setShipmentId(shipmentId);
        entity.setCurrentStatus(ShipmentStatus.LABEL_CREATED.name());
        entity.setStatusOccurredAt(now);
        entity.setStateExplanation("Shipment created; awaiting first accepted status update.");
        entity.setProcessedEventCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return shipmentRepository.save(entity);
    }

    /**
     * Appends one row to {@code shipment_event} (immutable audit — docs/ANALYSIS.md §7.1, §7.4).
     */
    private void saveAuditRow(
            IngestAuditContext audit, String storedEventId, Disposition disposition, boolean stateChanged) {
        ShipmentEventEntity entity = mapper.toNewEventEntity(
                audit.command(), storedEventId, disposition, stateChanged, audit.payloadHash(), audit.ingestedAt());
        eventRepository.save(entity);
    }

    /**
     * Writes projected snapshot and {@code stateExplanation} to {@code shipment}; recomputes
     * {@code processedEventCount} (docs/ANALYSIS.md §7.5, §7.7).
     */
    private void updateShipmentFromProjection(ShipmentEntity shipment, ProjectionResult projection, Instant now) {
        int processedCount = (int) eventRepository.countByShipmentIdAndDispositionIn(
                shipment.getShipmentId(), Disposition.processedDispositionNames());
        mapper.applySnapshot(
                shipment, projection.snapshot(), projection.stateExplanation(), processedCount);
        shipment.setUpdatedAt(now);
        shipmentRepository.save(shipment);
    }
}
