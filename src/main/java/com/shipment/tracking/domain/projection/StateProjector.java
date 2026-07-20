package com.shipment.tracking.domain.projection;

import com.shipment.tracking.domain.model.Disposition;
import com.shipment.tracking.domain.model.DomainEvent;
import com.shipment.tracking.domain.model.ShipmentSnapshot;
import com.shipment.tracking.domain.model.ShipmentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domain logic: derive current shipment status from accepted events only.
 *
 * <p>Business rules: docs/ANALYSIS.md §7.2 (out-of-order), §7.3 (same-instant conflicts),
 * §2 (status ordinal order). Wired via {@link com.shipment.tracking.config.DomainConfiguration}.
 */
public class StateProjector {

    private static final Comparator<DomainEvent> EVENT_ORDER =
            Comparator.comparing(DomainEvent::occurredAt)
                    .thenComparing(DomainEvent::receivedAt)
                    .thenComparingLong(DomainEvent::id);

    /**
     * Replays the full accepted timeline and returns the resulting snapshot plus explanation.
     * Used internally and by tests; ingest uses {@link #projectNewEvent}.
     */
    public ProjectionResult projectFromAcceptedEvents(String shipmentId, List<DomainEvent> acceptedEvents) {
        List<DomainEvent> sorted = acceptedEvents.stream().sorted(EVENT_ORDER).toList();
        ShipmentSnapshot snapshot = ShipmentSnapshot.empty(shipmentId);
        DomainEvent lastTrigger = null;
        Disposition lastDisposition = Disposition.ACCEPTED;
        boolean anyStateChange = false;

        for (DomainEvent effective : collapseConflictsAtSameInstant(sorted)) {
            ProjectionStep step = applyEvent(snapshot, effective);
            snapshot = step.snapshot();
            if (step.stateChanged()) {
                anyStateChange = true;
                lastDisposition = Disposition.ACCEPTED_STATE_CHANGED;
            } else if (snapshot.hasStatus()) {
                lastDisposition = Disposition.ACCEPTED_NO_STATE_CHANGE;
            }
            lastTrigger = effective;
        }

        if (!snapshot.hasStatus() && lastTrigger == null) {
            return new ProjectionResult(
                    snapshot, Disposition.ACCEPTED, false, "No accepted events yet.", null);
        }

        String explanation = buildExplanation(snapshot, lastTrigger, lastDisposition, anyStateChange);
        return new ProjectionResult(snapshot, lastDisposition, anyStateChange, explanation, lastTrigger);
    }

    /**
     * Projects one newly accepted event: replays history, then sets {@code stateChanged} only if
     * this ingest materially changed state vs {@code before} (docs/ANALYSIS.md §7.2).
     */
    public ProjectionResult projectNewEvent(
            ShipmentSnapshot before, List<DomainEvent> priorAccepted, DomainEvent incoming) {
        List<DomainEvent> all = new ArrayList<>(priorAccepted);
        all.add(incoming);
        ProjectionResult replayed = projectFromAcceptedEvents(before.shipmentId(), all);

        boolean stateChanged = hasMaterialStateChange(before, replayed.snapshot());
        Disposition disposition = stateChanged
                ? Disposition.ACCEPTED_STATE_CHANGED
                : (before.hasStatus() ? Disposition.ACCEPTED_NO_STATE_CHANGE : Disposition.ACCEPTED);
        String explanation = buildExplanation(replayed.snapshot(), incoming, disposition, stateChanged);
        return new ProjectionResult(replayed.snapshot(), disposition, stateChanged, explanation, incoming);
    }

    /**
     * Builds the human-readable {@code stateExplanation} string (docs/ANALYSIS.md §7.7).
     * Persisted on {@code shipment} at write time; GET reads stored value.
     */
    public String buildExplanation(
            ShipmentSnapshot after,
            DomainEvent trigger,
            Disposition disposition,
            boolean stateChanged) {
        if (trigger == null) {
            return "No shipment events processed yet.";
        }
        if (!after.hasStatus()) {
            return "No current status established.";
        }
        String status = after.currentStatus().name();
        String at = after.statusOccurredAt().toString();
        String evt = trigger.eventId();
        String partner = trigger.partner();

        if (disposition == Disposition.ACCEPTED_NO_STATE_CHANGE || !stateChanged) {
            return String.format(
                    "Status remains %s. Event %s (%s at %s) accepted for audit but did not change current state.",
                    status, evt, trigger.status().name(), trigger.occurredAt());
        }
        if (trigger.status() == ShipmentStatus.RETURNED && after.latestDeliveredAt() != null) {
            return String.format(
                    "Status %s at %s after DELIVERED at %s (event %s, partner %s).",
                    status, at, after.latestDeliveredAt(), evt, partner);
        }
        if (trigger.status() == ShipmentStatus.DELIVERY_EXCEPTION) {
            return String.format(
                    "Status %s at %s; exception preferred when conflicting at same time (event %s, partner %s).",
                    status, at, evt, partner);
        }
        return String.format(
                "Current status %s from event %s (partner %s) at %s.", status, evt, partner, at);
    }

    /**
     * Resolves same-instant conflicts: highest ordinal rank wins (docs/ANALYSIS.md §7.3, §2).
     */
    private List<DomainEvent> collapseConflictsAtSameInstant(List<DomainEvent> sorted) {
        Map<Instant, DomainEvent> byInstant = new LinkedHashMap<>();
        for (DomainEvent event : sorted) {
            byInstant.merge(
                    event.occurredAt(),
                    event,
                    (a, b) -> a.status().getOrdinalRank() >= b.status().getOrdinalRank() ? a : b);
        }
        return new ArrayList<>(byInstant.values());
    }

    /**
     * True when status or {@code statusOccurredAt} changed between before and after replay.
     */
    private boolean hasMaterialStateChange(ShipmentSnapshot before, ShipmentSnapshot after) {
        if (!after.hasStatus()) {
            return false;
        }
        if (!before.hasStatus()) {
            return true;
        }
        return before.currentStatus() != after.currentStatus()
                || !before.statusOccurredAt().equals(after.statusOccurredAt());
    }

    /**
     * Applies one effective event to the running snapshot (forward-only rules per §7.2).
     */
    private ProjectionStep applyEvent(ShipmentSnapshot snapshot, DomainEvent event) {
        if (!snapshot.hasStatus()) {
            return applyFirstStatus(snapshot, event);
        }
        if (event.status() == ShipmentStatus.RETURNED) {
            return applyReturned(snapshot, event);
        }
        if (isExceptionAfterReturned(snapshot, event)) {
            return ProjectionStep.noChange(snapshot);
        }
        if (isBackwardOrStale(snapshot, event)) {
            return ProjectionStep.noChange(snapshot);
        }
        if (shouldAdvanceStatus(snapshot, event)) {
            return ProjectionStep.changed(advance(snapshot, event));
        }
        return ProjectionStep.noChange(snapshot);
    }

    /**
     * Establishes the first current status from the earliest accepted event.
     */
    private ProjectionStep applyFirstStatus(ShipmentSnapshot snapshot, DomainEvent event) {
        if (event.status() == ShipmentStatus.RETURNED) {
            return ProjectionStep.noChange(snapshot);
        }
        Instant latestDelivered = switch (event.status()) {
            case DELIVERED, DELIVERY_EXCEPTION -> event.occurredAt();
            default -> null;
        };
        ShipmentSnapshot next = new ShipmentSnapshot(
                snapshot.shipmentId(),
                event.status(),
                event.occurredAt(),
                event.location(),
                latestDelivered,
                0);
        return ProjectionStep.changed(next);
    }

    /**
     * RETURNED only after delivery; {@code occurredAt} must be strictly after
     * {@code latestDeliveredAt} (docs/ANALYSIS.md §3 assumption table, §7).
     */
    private ProjectionStep applyReturned(ShipmentSnapshot snapshot, DomainEvent event) {
        if (snapshot.latestDeliveredAt() == null
                || !event.occurredAt().isAfter(snapshot.latestDeliveredAt())) {
            return ProjectionStep.noChange(snapshot);
        }
        return ProjectionStep.changed(advance(snapshot, event));
    }

    /**
     * Once RETURNED, a later EXCEPTION at the same timeline must not roll back display status.
     */
    private static boolean isExceptionAfterReturned(ShipmentSnapshot snapshot, DomainEvent event) {
        return event.status() == ShipmentStatus.DELIVERY_EXCEPTION
                && snapshot.currentStatus() == ShipmentStatus.RETURNED;
    }

    /**
     * Ignores statuses that would move backward in ordinal rank (docs/ANALYSIS.md §7.2).
     */
    private static boolean isBackwardOrStale(ShipmentSnapshot snapshot, DomainEvent event) {
        return !event.status().isForwardFrom(snapshot.currentStatus())
                && event.status() != ShipmentStatus.DELIVERY_EXCEPTION;
    }

    /**
     * Decides whether this event should update current status (forward step or same-instant upgrade).
     */
    private static boolean shouldAdvanceStatus(ShipmentSnapshot snapshot, DomainEvent event) {
        if (event.status().isForwardFrom(snapshot.currentStatus())) {
            return true;
        }
        if (event.status() == ShipmentStatus.DELIVERY_EXCEPTION
                && event.occurredAt().equals(snapshot.statusOccurredAt())) {
            return true;
        }
        return event.occurredAt().equals(snapshot.statusOccurredAt())
                && event.status().getOrdinalRank() > snapshot.currentStatus().getOrdinalRank();
    }

    /**
     * Produces the next snapshot after a state-changing event; tracks {@code latestDeliveredAt}
     * for the RETURNED rule (§7).
     */
    private ShipmentSnapshot advance(ShipmentSnapshot snapshot, DomainEvent event) {
        Instant latestDelivered = snapshot.latestDeliveredAt();
        if (event.status() == ShipmentStatus.DELIVERED) {
            latestDelivered = event.occurredAt();
        } else if (event.status() == ShipmentStatus.DELIVERY_EXCEPTION) {
            latestDelivered = event.occurredAt();
        }
        return new ShipmentSnapshot(
                snapshot.shipmentId(),
                event.status(),
                event.occurredAt(),
                event.location(),
                latestDelivered,
                snapshot.processedEventCount());
    }

    /** Internal result of applying a single event to a snapshot. */
    private record ProjectionStep(ShipmentSnapshot snapshot, boolean stateChanged) {
        /** Snapshot changed. */
        static ProjectionStep changed(ShipmentSnapshot snapshot) {
            return new ProjectionStep(snapshot, true);
        }

        /** Snapshot unchanged (e.g. out-of-order). */
        static ProjectionStep noChange(ShipmentSnapshot snapshot) {
            return new ProjectionStep(snapshot, false);
        }
    }
}
