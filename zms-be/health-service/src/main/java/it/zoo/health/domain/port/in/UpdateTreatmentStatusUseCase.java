package it.zoo.health.domain.port.in;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;

import java.util.UUID;

public interface UpdateTreatmentStatusUseCase {
    Treatment updateStatus(UUID id, TreatmentStatus newStatus, String performedBy);
}
