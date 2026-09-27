package it.zoo.feeding.domain.port.in;

public interface HandleAnimalEventUseCase {
    void handle(HandleAnimalEventCommand command);
}
