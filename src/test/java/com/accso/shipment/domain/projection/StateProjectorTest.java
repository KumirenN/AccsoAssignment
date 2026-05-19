package com.accso.shipment.domain.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.accso.shipment.domain.model.Disposition;
import com.accso.shipment.domain.model.DomainEvent;
import com.accso.shipment.domain.model.ShipmentSnapshot;
import com.accso.shipment.domain.model.ShipmentStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for projection rules (docs/ANALYSIS.md §7.2–§7.3). Complements walkthrough steps 4–6 in
 * {@link com.accso.shipment.ShipmentFlowIntegrationTest}.
 */
class StateProjectorTest {

    private StateProjector projector;
    private static final Instant T1 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-10T18:00:00Z");
    private static final Instant T3 = Instant.parse("2026-03-12T10:00:00Z");

    @BeforeEach
    void setUp() {
        projector = new StateProjector();
    }

    @Test
    void givenInTransitThenDelivered_whenProjectFromAcceptedEvents_thenDelivered() {
        // given
        List<DomainEvent> events = List.of(
                event(1, ShipmentStatus.IN_TRANSIT, T1),
                event(2, ShipmentStatus.DELIVERED, T2));

        // when
        ProjectionResult result = projector.projectFromAcceptedEvents("ship-1", events);

        // then
        assertThat(result.snapshot().currentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.stateChanged()).isTrue();
    }

    @Test
    void givenDeliveredThenOlderHandedToCarrier_whenProjectFromAcceptedEvents_thenStillDelivered() {
        // given — walkthrough step 4 (out-of-order)
        List<DomainEvent> events = List.of(
                event(1, ShipmentStatus.DELIVERED, T2),
                event(2, ShipmentStatus.HANDED_TO_CARRIER, T1));

        // when
        ProjectionResult result = projector.projectFromAcceptedEvents("ship-1", events);

        // then
        assertThat(result.snapshot().currentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void givenDeliveredAndExceptionAtSameInstant_whenProjectFromAcceptedEvents_thenExceptionWins() {
        // given — walkthrough step 5
        List<DomainEvent> events = List.of(
                event(1, ShipmentStatus.DELIVERED, T2),
                event(2, ShipmentStatus.DELIVERY_EXCEPTION, T2));

        // when
        ProjectionResult result = projector.projectFromAcceptedEvents("ship-1", events);

        // then
        assertThat(result.snapshot().currentStatus()).isEqualTo(ShipmentStatus.DELIVERY_EXCEPTION);
        assertThat(result.stateExplanation()).contains("exception");
    }

    @Test
    void givenDeliveredExceptionThenReturned_whenProjectFromAcceptedEvents_thenReturned() {
        // given — walkthrough step 5 + 6
        List<DomainEvent> events = List.of(
                event(1, ShipmentStatus.DELIVERED, T2),
                event(2, ShipmentStatus.DELIVERY_EXCEPTION, T2),
                event(3, ShipmentStatus.RETURNED, T3));

        // when
        ProjectionResult result = projector.projectFromAcceptedEvents("ship-1", events);

        // then
        assertThat(result.snapshot().currentStatus()).isEqualTo(ShipmentStatus.RETURNED);
    }

    @Test
    void givenDeliveredThenReturned_whenProjectFromAcceptedEvents_thenReturnedWithDeliveryTime() {
        // given — walkthrough step 6 (direct DELIVERED → RETURNED)
        List<DomainEvent> events = List.of(
                event(1, ShipmentStatus.DELIVERED, T2),
                event(2, ShipmentStatus.RETURNED, T3));

        // when
        ProjectionResult result = projector.projectFromAcceptedEvents("ship-1", events);

        // then
        assertThat(result.snapshot().currentStatus()).isEqualTo(ShipmentStatus.RETURNED);
        assertThat(result.snapshot().latestDeliveredAt()).isEqualTo(T2);
    }

    @Test
    void givenReturnedBeforeDelivered_whenProjectNewDelivered_thenDelivered() {
        // given
        ShipmentSnapshot before = new ShipmentSnapshot("ship-1", ShipmentStatus.IN_TRANSIT, T1, null, null, 1);
        DomainEvent returned = event(2, ShipmentStatus.RETURNED, T1);
        DomainEvent delivered = event(3, ShipmentStatus.DELIVERED, T2);

        // when
        ProjectionResult result = projector.projectNewEvent(before, List.of(returned), delivered);

        // then
        assertThat(result.snapshot().currentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void givenNoStateChangeDisposition_whenBuildExplanation_thenMentionsRemainsDelivered() {
        // given
        DomainEvent trigger = event(1, ShipmentStatus.IN_TRANSIT, T1);
        ShipmentSnapshot after =
                new ShipmentSnapshot("ship-1", ShipmentStatus.DELIVERED, T2, null, T2, 2);

        // when
        String explanation =
                projector.buildExplanation(after, trigger, Disposition.ACCEPTED_NO_STATE_CHANGE, false);

        // then
        assertThat(explanation).contains("remains DELIVERED");
    }

    private static DomainEvent event(long id, ShipmentStatus status, Instant occurredAt) {
        return new DomainEvent(id, "dhl", "evt-" + id, "ship-1", status, occurredAt, occurredAt.plusSeconds(5), "AMS");
    }
}
