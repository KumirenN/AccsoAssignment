package com.shipment.tracking.api;

import com.shipment.tracking.api.dto.ShipmentEventHistoryResponse;
import com.shipment.tracking.api.dto.ShipmentResponse;
import com.shipment.tracking.application.GetShipmentHistoryService;
import com.shipment.tracking.application.GetShipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read APIs (docs/ANALYSIS.md §8): current shipment view and full event audit.
 */
@RestController
@RequestMapping("/shipments")
public class ShipmentQueryController {

    private final GetShipmentService getShipmentService;
    private final GetShipmentHistoryService getHistoryService;

    /**
     * Creates the query controller with read-side application services.
     */
    public ShipmentQueryController(
            GetShipmentService getShipmentService, GetShipmentHistoryService getHistoryService) {
        this.getShipmentService = getShipmentService;
        this.getHistoryService = getHistoryService;
    }

    /**
     * Returns projected current status and {@code stateExplanation} (docs/ANALYSIS.md §7.7).
     */
    @GetMapping("/{shipmentId}")
    public ShipmentResponse getShipment(@PathVariable String shipmentId) {
        return getShipmentService.getById(shipmentId);
    }

    /**
     * Returns chronological audit of all ingests, all dispositions (docs/ANALYSIS.md §7.8).
     */
    @GetMapping("/{shipmentId}/events")
    public ShipmentEventHistoryResponse getHistory(@PathVariable String shipmentId) {
        return getHistoryService.getEvents(shipmentId);
    }
}
