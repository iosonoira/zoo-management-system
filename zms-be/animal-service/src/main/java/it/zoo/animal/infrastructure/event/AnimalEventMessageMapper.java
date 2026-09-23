package it.zoo.animal.infrastructure.event;

import it.zoo.animal.domain.event.AnimalEvent;
import it.zoo.animal.domain.event.AnimalRegistered;
import it.zoo.animal.domain.event.AnimalStatusChanged;
import it.zoo.animal.domain.event.AnimalTransferred;

public final class AnimalEventMessageMapper {

    private AnimalEventMessageMapper() {}

    public static AnimalEventMessage toMessage(AnimalEvent event) {
        return switch (event) {
            case AnimalRegistered e -> new AnimalEventMessage(
                    e.eventId(),
                    "ANIMAL_REGISTERED",
                    e.occurredAt(),
                    e.animalId(),
                    e.performedBy(),
                    new AnimalEventMessage.AnimalRegisteredPayload(
                            e.name(),
                            e.species(),
                            e.dangerous(),
                            e.habitat().name(),
                            e.enclosureId()
                    )
            );
            case AnimalStatusChanged e -> new AnimalEventMessage(
                    e.eventId(),
                    "ANIMAL_STATUS_CHANGED",
                    e.occurredAt(),
                    e.animalId(),
                    e.performedBy(),
                    new AnimalEventMessage.AnimalStatusChangedPayload(
                            e.previousStatus().name(),
                            e.newStatus().name(),
                            e.name(),
                            e.species()
                    )
            );
            case AnimalTransferred e -> new AnimalEventMessage(
                    e.eventId(),
                    "ANIMAL_TRANSFERRED",
                    e.occurredAt(),
                    e.animalId(),
                    e.performedBy(),
                    new AnimalEventMessage.AnimalTransferredPayload(
                            e.fromEnclosureId(),
                            e.toEnclosureId(),
                            e.name(),
                            e.species(),
                            e.dangerous()
                    )
            );
        };
    }
}
