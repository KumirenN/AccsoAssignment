package com.accso.shipment.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link PartnerConfigProperties} (Phase 2 partner dedupe config). */
@Configuration
@EnableConfigurationProperties(PartnerConfigProperties.class)
public class PartnerConfiguration {}
