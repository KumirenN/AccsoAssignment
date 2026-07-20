package com.shipment.tracking.application;

import com.shipment.tracking.application.command.IngestShipmentEventCommand;
import java.time.Instant;

/**
 * Per-request values computed once at the start of ingest: command, SHA-256 of raw JSON (§7.1
 * payload mismatch), and server ingest timestamp for audit rows.
 */
record IngestAuditContext(IngestShipmentEventCommand command, String payloadHash, Instant ingestedAt) {}
