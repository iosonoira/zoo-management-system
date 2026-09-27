package it.zoo.feeding.domain.port.in;

import it.zoo.feeding.domain.model.Feeding;

public interface RecordFeedingUseCase {
    Feeding record(RecordFeedingCommand cmd);
}
