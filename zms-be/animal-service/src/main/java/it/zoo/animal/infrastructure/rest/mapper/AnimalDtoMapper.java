package it.zoo.animal.infrastructure.rest.mapper;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.infrastructure.rest.dto.AnimalResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface AnimalDtoMapper {
    AnimalResponse toResponse(Animal animal);
    List<AnimalResponse> toResponseList(List<Animal> animals);
}
