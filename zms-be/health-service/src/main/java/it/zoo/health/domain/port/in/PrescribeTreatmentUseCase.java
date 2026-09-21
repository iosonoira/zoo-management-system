package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.Treatment;

public interface PrescribeTreatmentUseCase {
    Treatment prescribe(PrescribeTreatmentCommand cmd);
}
