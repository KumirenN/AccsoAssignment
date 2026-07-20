package com.shipment.tracking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Mirrors {@code docs/WALKTHROUGH.md} Phase 1 steps 1–10 for {@code ship-demo-001}.
 * Run in order via {@link TestMethodOrder}; matches manual QA curl sequence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentFlowIntegrationTest {

    private static final String DEMO_SHIPMENT = "ship-demo-001";
    private static final String PARTNER = "dhl";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Walkthrough steps 1–10 (docs/WALKTHROUGH.md Phase 1) ---

    @Test
    @Order(1)
    void step01_givenNewShipment_whenInTransitPosted_thenAcceptedWithInTransit() throws Exception {
        postEvent(DEMO_SHIPMENT, "evt-1", "IN_TRANSIT", "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.stateChanged").value(true))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"));
    }

    @Test
    @Order(2)
    void step02_givenInTransit_whenDeliveredPosted_thenCurrentStatusDelivered() throws Exception {
        postEvent(DEMO_SHIPMENT, "evt-2", "DELIVERED", "2026-03-10T18:00:00Z", "2026-03-10T18:00:10Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"));
    }

    @Test
    @Order(3)
    void step03_givenDelivered_whenSameEventIdRepPosted_thenDuplicateWithoutStateChange() throws Exception {
        postEvent(DEMO_SHIPMENT, "evt-2", "DELIVERED", "2026-03-10T18:00:00Z", "2026-03-10T19:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.stateChanged").value(false))
                .andExpect(jsonPath("$.payloadMismatch").value(true))
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"));
    }

    @Test
    @Order(4)
    void step04_givenDelivered_whenOlderHandedToCarrierPosted_thenNoStateChange() throws Exception {
        postEvent(
                        DEMO_SHIPMENT,
                        "evt-3",
                        "HANDED_TO_CARRIER",
                        "2026-03-10T11:00:00Z",
                        "2026-03-10T20:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.stateChanged").value(false))
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"));
    }

    @Test
    @Order(5)
    void step05_givenDeliveredAtInstant_whenExceptionAtSameInstant_thenExceptionWins() throws Exception {
        postEvent(
                        DEMO_SHIPMENT,
                        "evt-4",
                        "DELIVERY_EXCEPTION",
                        "2026-03-10T18:00:00Z",
                        "2026-03-10T18:00:15Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("DELIVERY_EXCEPTION"));
    }

    @Test
    @Order(6)
    void step06_givenException_whenReturnedAfterDelivery_thenCurrentStatusReturned() throws Exception {
        postEvent(DEMO_SHIPMENT, "evt-5", "RETURNED", "2026-03-12T10:00:00Z", "2026-03-12T10:00:05Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("RETURNED"));
    }

    @Test
    @Order(7)
    void step07_givenShipment_whenInvalidStatusPosted_then400AndAudited() throws Exception {
        postEvent(DEMO_SHIPMENT, "evt-bad", "NOT_A_REAL_STATUS", "2026-03-12T11:00:00Z", "2026-03-12T11:00:05Z")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/shipments/{id}/events", DEMO_SHIPMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.eventId == 'evt-bad')].disposition")
                        .value("REJECTED_INVALID"));
    }

    @Test
    @Order(8)
    void step08_givenReturnedShipment_whenGetCurrent_thenStateExplanationPresent() throws Exception {
        mockMvc.perform(get("/shipments/{id}", DEMO_SHIPMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("RETURNED"))
                .andExpect(jsonPath("$.statusOccurredAt").value("2026-03-12T10:00:00Z"))
                .andExpect(jsonPath("$.processedEventCount").value(5))
                .andExpect(jsonPath("$.stateExplanation").isNotEmpty());
    }

    @Test
    @Order(9)
    void step09_givenDemoHistory_whenGetEvents_thenChronologicalWithAllDispositions() throws Exception {
        mockMvc.perform(get("/shipments/{id}/events", DEMO_SHIPMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(7))
                .andExpect(jsonPath("$.events[0].status").value("HANDED_TO_CARRIER"))
                .andExpect(jsonPath("$.events[0].occurredAt").value("2026-03-10T11:00:00Z"))
                .andExpect(jsonPath("$.events[?(@.disposition == 'DUPLICATE')]").exists())
                .andExpect(jsonPath("$.events[?(@.disposition == 'REJECTED_INVALID')]").exists());
    }

    @Test
    @Order(10)
    void step10_givenUnknownId_whenGetShipment_then404() throws Exception {
        mockMvc.perform(get("/shipments/ship-does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    void step10b_givenUnknownId_whenGetEvents_then404() throws Exception {
        mockMvc.perform(get("/shipments/ship-does-not-exist/events")).andExpect(status().isNotFound());
    }

    // --- Standalone scenarios (separate shipment ids) ---

    @Test
    void givenOnlyInvalidIngest_whenGetEvents_then200ButGetShipment404() throws Exception {
        String shipmentId = "ship-bad-001";

        postEvent(shipmentId, "evt-bad-ship-only", "NOT_A_STATUS", "2026-03-10T12:00:00Z", "2026-03-10T12:00:05Z")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/shipments/{id}/events", shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].disposition").value("REJECTED_INVALID"));

        mockMvc.perform(get("/shipments/{id}", shipmentId)).andExpect(status().isNotFound());
    }

    private ResultActions postEvent(
            String shipmentId,
            String eventId,
            String status,
            String occurredAt,
            String receivedAt)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("eventId", eventId);
        body.put("partner", PARTNER);
        body.put("shipmentId", shipmentId);
        body.put("status", status);
        body.put("occurredAt", occurredAt);
        body.put("receivedAt", receivedAt);
        body.put("location", "Amsterdam");
        return mockMvc.perform(post("/shipment-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
