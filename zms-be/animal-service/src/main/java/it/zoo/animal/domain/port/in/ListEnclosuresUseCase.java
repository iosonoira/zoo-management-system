package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Enclosure;
import java.util.List;

public interface ListEnclosuresUseCase {

    List<Enclosure> listAll();
}
