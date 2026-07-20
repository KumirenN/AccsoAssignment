package com.shipment.tracking.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-side partner dedupe configuration (docs/ANALYSIS.md §4.6, §6.1).
 *
 * <pre>
 * shipment:
 *   partners:
 *     dhl:
 *       dedupe-strategy: event-id
 *     acme:
 *       dedupe-strategy: natural-key
 * </pre>
 */
@ConfigurationProperties(prefix = "shipment")
public class PartnerConfigProperties {

    private Map<String, PartnerSettings> partners = new HashMap<>();

    public Map<String, PartnerSettings> getPartners() {
        return partners;
    }

    public void setPartners(Map<String, PartnerSettings> partners) {
        this.partners = partners;
    }

    /** Per-partner settings from {@code application.yml}. */
    public static class PartnerSettings {

        /** {@code event-id} or {@code natural-key}. */
        private String dedupeStrategy = "event-id";

        public String getDedupeStrategy() {
            return dedupeStrategy;
        }

        public void setDedupeStrategy(String dedupeStrategy) {
            this.dedupeStrategy = dedupeStrategy;
        }

        public boolean isNaturalKey() {
            return "natural-key".equalsIgnoreCase(dedupeStrategy);
        }
    }
}
