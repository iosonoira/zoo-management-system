package it.zoo.feeding.infrastructure.rest.dto;

import it.zoo.feeding.domain.enums.PlanStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFeedingPlanStatusRequest(
        @NotNull PlanStatus status
) {}
