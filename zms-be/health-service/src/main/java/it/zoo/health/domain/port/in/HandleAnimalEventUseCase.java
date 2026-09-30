package it.zoo.health.domain.port.in;

public interface HandleAnimalEventUseCase {
    void handle(HandleAnimalEventCommand command);
}
