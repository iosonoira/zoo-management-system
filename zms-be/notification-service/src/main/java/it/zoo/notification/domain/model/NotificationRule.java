package it.zoo.notification.domain.model;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;

public final class NotificationRule {

    private NotificationRule() {
    }

    public static Severity severityOf(HandleAnimalEventCommand command) {
        return switch (command.eventType()) {
            case ANIMAL_REGISTERED -> Severity.INFO;
            case ANIMAL_STATUS_CHANGED -> {
                String status = command.newStatus();
                if ("DECEASED".equals(status)) {
                    yield Severity.CRITICAL;
                } else if ("UNDER_OBSERVATION".equals(status) || "IN_TREATMENT".equals(status)) {
                    yield Severity.WARNING;
                } else {
                    yield Severity.INFO;
                }
            }
            case ANIMAL_TRANSFERRED -> Boolean.TRUE.equals(command.dangerous()) ? Severity.WARNING : Severity.INFO;
        };
    }

    public static String messageOf(HandleAnimalEventCommand command) {
        return switch (command.eventType()) {
            case ANIMAL_REGISTERED ->
                String.format("%s (%s) was registered", command.name(), command.species());
            case ANIMAL_STATUS_CHANGED ->
                String.format("%s (%s) status changed from %s to %s",
                    command.name(), command.species(), command.previousStatus(), command.newStatus());
            case ANIMAL_TRANSFERRED ->
                String.format("%s (%s) was transferred from enclosure %s to enclosure %s",
                    command.name(), command.species(), command.fromEnclosureId(), command.toEnclosureId());
        };
    }
}
