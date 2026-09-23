package it.zoo.animal.domain.port.out;

import it.zoo.animal.domain.model.Enclosure;
import java.util.List;
import java.util.UUID;

public interface EnclosureRepository {

    List<Enclosure> findAll();

    boolean existsById(UUID id);
}
