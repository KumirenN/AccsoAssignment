package com.shipment.tracking.application;

import com.shipment.tracking.api.dto.ShipmentResponse;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for {@code GET /shipments/{id}} — current projected view (docs/ANALYSIS.md §7.7, §8).
 */
@Service
public class GetShipmentService {

    private final ShipmentRepository shipmentRepository;

    /**
     * Creates the service with the shipment repository.
     */
    public GetShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    /**
     * Loads the {@code shipment} row only. If never created (e.g. invalid-only ingests per §7.4),
     * throws {@link ShipmentNotFoundException} — use {@link GetShipmentHistoryService} for audit-only ids.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getById(String shipmentId) {
        return shipmentRepository
                .findById(shipmentId)
                .map(ShipmentResponse::fromEntity)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
    }
}
