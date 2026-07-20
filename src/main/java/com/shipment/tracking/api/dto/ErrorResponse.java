package com.shipment.tracking.api.dto;

import java.util.Map;

/**
 * Standard API error body for 4xx responses (docs/ANALYSIS.md §8).
 */
public class ErrorResponse {

    private String code;
    private String message;
    private Map<String, String> fields;

    /** Default constructor for JSON deserialization. */
    public ErrorResponse() {}

    /**
     * Creates an error with code and message (e.g. INVALID_STATUS, SHIPMENT_NOT_FOUND).
     */
    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Creates a validation error with per-field messages.
     */
    public ErrorResponse(String code, String message, Map<String, String> fields) {
        this.code = code;
        this.message = message;
        this.fields = fields;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }
}
