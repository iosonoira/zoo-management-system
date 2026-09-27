package it.zoo.feeding.domain.port.out;

import it.zoo.feeding.domain.model.Feeding;

import java.util.List;
import java.util.UUID;

public interface FeedingRepository {
    Feeding save(Feeding feeding);
    List<Feeding> findPageByPlanId(UUID planId, int page, int size);
    long countByPlanId(UUID planId);
}
