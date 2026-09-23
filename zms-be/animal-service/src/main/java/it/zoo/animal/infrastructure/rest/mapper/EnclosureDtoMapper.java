package it.zoo.animal.infrastructure.rest.mapper;

import it.zoo.animal.domain.model.Enclosure;
import it.zoo.animal.infrastructure.rest.dto.EnclosureResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface EnclosureDtoMapper {
    EnclosureResponse toResponse(Enclosure enclosure);
    List<EnclosureResponse> toResponseList(List<Enclosure> enclosures);
}
