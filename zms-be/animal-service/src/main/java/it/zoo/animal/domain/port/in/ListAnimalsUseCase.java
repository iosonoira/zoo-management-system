package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.AnimalPage;

public interface ListAnimalsUseCase {

    int MAX_PAGE_SIZE = 100;

    AnimalPage list(int page, int size);
}
