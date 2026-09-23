package it.zoo.animal.domain.port.out;

import it.zoo.animal.domain.event.AnimalEvent;

public interface AnimalEventPublisher {
    void publish(AnimalEvent event);
}
