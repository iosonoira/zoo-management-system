package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Animal;
import java.util.List;

public interface ListAnimalsUseCase {
    List<Animal> listAll();
}
