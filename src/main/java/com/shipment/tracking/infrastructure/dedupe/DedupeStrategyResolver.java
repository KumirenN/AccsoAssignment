package com.shipment.tracking.infrastructure.dedupe;

import com.shipment.tracking.application.dedupe.DedupeStrategy;
import com.shipment.tracking.infrastructure.config.PartnerConfigProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link DedupeStrategy} from {@code application.yml} partner config (docs/ANALYSIS.md
 * §6.1).
 */
@Component
public class DedupeStrategyResolver {

    private final PartnerConfigProperties config;
    private final EventIdDedupeStrategy eventIdStrategy;
    private final NaturalKeyDedupeStrategy naturalKeyStrategy;

    public DedupeStrategyResolver(
            PartnerConfigProperties config,
            EventIdDedupeStrategy eventIdStrategy,
            NaturalKeyDedupeStrategy naturalKeyStrategy) {
        this.config = config;
        this.eventIdStrategy = eventIdStrategy;
        this.naturalKeyStrategy = naturalKeyStrategy;
    }

    /**
     * Returns the configured strategy for {@code partner}, defaulting to event-id when unknown.
     */
    public DedupeStrategy resolve(String partner) {
        PartnerConfigProperties.PartnerSettings settings = config.getPartners().get(partner);
        if (settings != null && settings.isNaturalKey()) {
            return naturalKeyStrategy;
        }
        return eventIdStrategy;
    }
}
