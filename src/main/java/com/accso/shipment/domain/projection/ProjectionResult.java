package com.accso.shipment.domain.projection;

import com.accso.shipment.domain.model.Disposition;
import com.accso.shipment.domain.model.DomainEvent;
import com.accso.shipment.domain.model.ShipmentSnapshot;

/**
 * Output of {@link StateProjector}: new snapshot, audit disposition, change flag, and explanation
 * text for persistence (docs/ANALYSIS.md §7.6, §7.7).
 */
public record ProjectionResult(
        ShipmentSnapshot snapshot,
        Disposition disposition,
        boolean stateChanged,
        String stateExplanation,
        DomainEvent triggeringEvent) {}
