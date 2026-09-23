package it.zoo.notification.domain;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.model.NotificationRule;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationRuleTest {

    @Test
    void shouldAssignInfoSeverityForAnimalRegistered() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );

        assertEquals(Severity.INFO, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignInfoMessageForAnimalRegistered() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );

        assertEquals("Leo (Lion) was registered", NotificationRule.messageOf(cmd));
    }

    @Test
    void shouldAssignCriticalSeverityWhenStatusChangedToDeceased() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "HEALTHY", "DECEASED", null, null
        );

        assertEquals(Severity.CRITICAL, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignWarningMessageForStatusChangedToDeceased() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "HEALTHY", "DECEASED", null, null
        );

        assertEquals("Leo (Lion) status changed from HEALTHY to DECEASED", NotificationRule.messageOf(cmd));
    }

    @Test
    void shouldAssignWarningSeverityWhenStatusChangedToUnderObservation() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "HEALTHY", "UNDER_OBSERVATION", null, null
        );

        assertEquals(Severity.WARNING, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignWarningSeverityWhenStatusChangedToInTreatment() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "HEALTHY", "IN_TREATMENT", null, null
        );

        assertEquals(Severity.WARNING, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignInfoSeverityWhenStatusChangedToHealthy() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "UNDER_OBSERVATION", "HEALTHY", null, null
        );

        assertEquals(Severity.INFO, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignInfoSeverityWhenStatusChangedToUnknownStatus() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "HEALTHY", "UNKNOWN_STATUS", null, null
        );

        assertEquals(Severity.INFO, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignInfoMessageForStatusChanged() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "vet", "Leo", "Lion", false, "HEALTHY", "UNDER_OBSERVATION", null, null
        );

        assertEquals("Leo (Lion) status changed from HEALTHY to UNDER_OBSERVATION", NotificationRule.messageOf(cmd));
    }

    @Test
    void shouldAssignWarningSeverityWhenTransferredDangerous() {
        UUID enclosureFrom = UUID.randomUUID();
        UUID enclosureTo = UUID.randomUUID();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_TRANSFERRED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", true, null, null, enclosureFrom, enclosureTo
        );

        assertEquals(Severity.WARNING, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignInfoSeverityWhenTransferredNotDangerous() {
        UUID enclosureFrom = UUID.randomUUID();
        UUID enclosureTo = UUID.randomUUID();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_TRANSFERRED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, enclosureFrom, enclosureTo
        );

        assertEquals(Severity.INFO, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignInfoSeverityWhenTransferredDangerousIsNull() {
        UUID enclosureFrom = UUID.randomUUID();
        UUID enclosureTo = UUID.randomUUID();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_TRANSFERRED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", null, null, null, enclosureFrom, enclosureTo
        );

        assertEquals(Severity.INFO, NotificationRule.severityOf(cmd));
    }

    @Test
    void shouldAssignTransferMessageForAnimalTransferred() {
        UUID enclosureFrom = UUID.randomUUID();
        UUID enclosureTo = UUID.randomUUID();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_TRANSFERRED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", true, null, null, enclosureFrom, enclosureTo
        );

        String expected = String.format("Leo (Lion) was transferred from enclosure %s to enclosure %s", enclosureFrom, enclosureTo);
        assertEquals(expected, NotificationRule.messageOf(cmd));
    }
}
