package com.accso.shipment;

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
 * Phase 2 change request — mirrors {@code docs/WALKTHROUGH.md} steps 11–12 for partner {@code acme}
 * (natural-key dedupe without {@code eventId}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChangeRequestIntegrationTest {

    private static final String ACME_SHIPMENT = "ship-acme-001";
    private static final String PARTNER = "acme";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(11)
    void step11_givenAcmePartner_whenInTransitPostedWithoutEventId_thenAccepted() throws Exception {
        postAcmeEvent("IN_TRANSIT", "2026-04-01T09:00:00Z", "2026-04-01T09:00:01Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.stateChanged").value(true))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"));
    }

    @Test
    @Order(12)
    void step12_givenAcmeInTransit_whenSameUpdateDifferentReceivedAt_thenDuplicate() throws Exception {
        postAcmeEvent("IN_TRANSIT", "2026-04-01T09:00:00Z", "2026-04-01T14:30:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.stateChanged").value(false))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"));
    }

    @Test
    void givenDhlPartner_whenPostWithoutEventId_then400() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("partner", "dhl");
        body.put("shipmentId", "ship-dhl-no-event");
        body.put("status", "IN_TRANSIT");
        body.put("occurredAt", "2026-04-01T10:00:00Z");
        body.put("receivedAt", "2026-04-01T10:00:01Z");

        mockMvc.perform(post("/shipment-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_EVENT_ID"));
    }

    private ResultActions postAcmeEvent(String status, String occurredAt, String receivedAt)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("partner", PARTNER);
        body.put("shipmentId", ACME_SHIPMENT);
        body.put("status", status);
        body.put("occurredAt", occurredAt);
        body.put("receivedAt", receivedAt);
        body.put("location", "Cape Town");
        return mockMvc.perform(post("/shipment-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
