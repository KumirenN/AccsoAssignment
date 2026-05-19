package com.accso.shipment.config;

import com.accso.shipment.domain.projection.StateProjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires domain types as Spring beans without putting framework annotations on domain code.
 */
@Configuration
public class DomainConfiguration {

    /**
     * Stateless status projection engine (docs/ANALYSIS.md §7.2–§7.3).
     */
    @Bean
    StateProjector stateProjector() {
        return new StateProjector();
    }
}
