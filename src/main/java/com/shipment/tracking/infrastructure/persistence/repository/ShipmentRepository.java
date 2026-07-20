package com.shipment.tracking.infrastructure.persistence.repository;

import com.shipment.tracking.infrastructure.persistence.entity.ShipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the {@code shipment} table — projected current state (docs/ANALYSIS.md §7.7, §8).
 */
public interface ShipmentRepository extends JpaRepository<ShipmentEntity, String> {}
