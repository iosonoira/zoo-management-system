package it.zoo.notification.domain.port.in;

public interface HandleAnimalEventUseCase {
    void handle(HandleAnimalEventCommand command);
}
