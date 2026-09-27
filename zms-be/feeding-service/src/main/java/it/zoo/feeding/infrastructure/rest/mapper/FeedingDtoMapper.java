package it.zoo.feeding.infrastructure.rest.mapper;

import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.infrastructure.rest.dto.FeedingPlanResponse;
import it.zoo.feeding.infrastructure.rest.dto.FeedingResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface FeedingDtoMapper {
    FeedingPlanResponse toResponse(FeedingPlan plan);
    List<FeedingPlanResponse> toResponseList(List<FeedingPlan> plans);
    FeedingResponse toResponse(Feeding feeding);
    List<FeedingResponse> toFeedingResponseList(List<Feeding> feedings);
}
