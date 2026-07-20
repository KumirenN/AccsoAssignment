package com.shipment.tracking.api.exception;

import com.shipment.tracking.api.dto.ErrorResponse;
import com.shipment.tracking.application.ShipmentNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to JSON error responses (docs/ANALYSIS.md §8 — 404 for unknown shipment).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Unknown shipment id on GET endpoints (no row in {@code shipment} and none in audit for history).
     */
    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ShipmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SHIPMENT_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Jakarta Bean Validation failures on request DTOs (e.g. missing {@code eventId}).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        err -> err.getField(),
                        err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "invalid",
                        (a, b) -> a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fields));
    }
}
