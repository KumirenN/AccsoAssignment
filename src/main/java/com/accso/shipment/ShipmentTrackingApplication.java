package com.accso.shipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the shipment tracking microservice (docs/ANALYSIS.md §10). */
@SpringBootApplication
public class ShipmentTrackingApplication {

    /** Starts the application. */
    public static void main(String[] args) {
        SpringApplication.run(ShipmentTrackingApplication.class, args);
    }
}
