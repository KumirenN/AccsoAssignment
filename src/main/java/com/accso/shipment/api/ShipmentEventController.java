package com.accso.shipment.api;

import com.accso.shipment.api.dto.ErrorResponse;
import com.accso.shipment.api.dto.IngestShipmentEventRequest;
import com.accso.shipment.application.IngestShipmentEventService;
import com.accso.shipment.application.command.IngestShipmentEventCommand;
import com.accso.shipment.application.result.IngestResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for {@code POST /shipment-events} (docs/ANALYSIS.md §8).
 * Bean Validation on the request body; business rules in {@link IngestShipmentEventService}.
 */
@RestController
public class ShipmentEventController {

    private final IngestShipmentEventService ingestService;
    private final ObjectMapper objectMapper;

    /**
     * Creates the controller with ingest service and JSON mapper.
     */
    public ShipmentEventController(IngestShipmentEventService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingests one courier webhook. Returns 400 for invalid status (§7.4), 200 for accepted and
     * duplicate (§7.1).
     */
    @PostMapping("/shipment-events")
    public ResponseEntity<?> ingest(@Valid @RequestBody IngestShipmentEventRequest request)
            throws JsonProcessingException {
        String rawPayload = objectMapper.writeValueAsString(request);
        IngestShipmentEventCommand command = toCommand(request, rawPayload);

        IngestResult result = ingestService.ingest(command);
        if (result.missingEventId()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            "MISSING_EVENT_ID",
                            "eventId is required for partner: " + request.getPartner()));
        }
        if (result.invalidStatus()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            "INVALID_STATUS",
                            "Unknown or invalid shipment status: " + request.getStatus()));
        }
        return ResponseEntity.ok(result.response());
    }

    /**
     * Maps the REST DTO to an application command, including canonical JSON for audit/hash.
     */
    private static IngestShipmentEventCommand toCommand(
            IngestShipmentEventRequest request, String rawPayload) {
        return new IngestShipmentEventCommand(
                request.getEventId(),
                request.getPartner(),
                request.getShipmentId(),
                request.getStatus(),
                request.getOccurredAt(),
                request.getReceivedAt(),
                request.getLocation(),
                rawPayload);
    }
}
