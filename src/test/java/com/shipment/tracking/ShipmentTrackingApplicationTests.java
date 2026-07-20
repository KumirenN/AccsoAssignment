package com.shipment.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipment.tracking.api.ShipmentEventController;
import com.shipment.tracking.api.ShipmentQueryController;
import com.shipment.tracking.application.GetShipmentHistoryService;
import com.shipment.tracking.application.GetShipmentService;
import com.shipment.tracking.application.IngestShipmentEventService;
import com.shipment.tracking.domain.projection.StateProjector;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentEventRepository;
import com.shipment.tracking.infrastructure.persistence.repository.ShipmentRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Application smoke tests for {@link ShipmentTrackingApplication}.
 *
 * <p>Uses {@link UseMainMethod#ALWAYS} so the real {@code main} method runs when the context starts
 * (same entry point as production). See Spring Boot "Using the Test Configuration Main Method".
 */
@SpringBootTest(
        classes = ShipmentTrackingApplication.class,
        useMainMethod = UseMainMethod.ALWAYS,
        webEnvironment = WebEnvironment.NONE)
class ShipmentTrackingApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentEventRepository shipmentEventRepository;

    @Test
    void givenShipmentTrackingApplicationClass_whenInspected_thenIsSpringBootEntryPoint() throws Exception {
        assertThat(ShipmentTrackingApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
        assertThat(ShipmentTrackingApplication.class.getDeclaredMethod("main", String[].class))
                .isNotNull();
    }

    @Test
    void givenMainMethod_whenContextStarts_thenApplicationContextIsActive() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getId()).isNotBlank();
    }

    @Test
    void givenShipmentTrackingApplication_whenContextStarts_thenApiLayerIsWired() {
        assertThat(applicationContext.getBean(ShipmentEventController.class)).isNotNull();
        assertThat(applicationContext.getBean(ShipmentQueryController.class)).isNotNull();
    }

    @Test
    void givenShipmentTrackingApplication_whenContextStarts_thenApplicationServicesAreWired() {
        assertThat(applicationContext.getBean(IngestShipmentEventService.class)).isNotNull();
        assertThat(applicationContext.getBean(GetShipmentService.class)).isNotNull();
        assertThat(applicationContext.getBean(GetShipmentHistoryService.class)).isNotNull();
    }

    @Test
    void givenShipmentTrackingApplication_whenContextStarts_thenDomainAndPersistenceAreWired() {
        assertThat(applicationContext.getBean(StateProjector.class)).isNotNull();
        assertThat(shipmentRepository).isNotNull();
        assertThat(shipmentEventRepository).isNotNull();
    }

    @Test
    void givenShipmentTrackingApplication_whenLiquibaseRuns_thenPhase1TablesExist() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment_event", Integer.class))
                .isZero();
    }

    @Test
    void givenShipmentTrackingApplication_whenFreshDatabase_thenRepositoriesAreEmpty() {
        assertThat(shipmentRepository.count()).isZero();
        assertThat(shipmentEventRepository.count()).isZero();
    }
}
