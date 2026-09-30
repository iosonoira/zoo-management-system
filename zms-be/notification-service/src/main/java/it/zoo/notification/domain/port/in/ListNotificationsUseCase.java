package it.zoo.notification.domain.port.in;

import it.zoo.notification.domain.model.NotificationPage;

import java.util.List;
import java.util.UUID;

public interface ListNotificationsUseCase {

    int MAX_PAGE_SIZE = 100;

    NotificationPage list(UUID animalId, List<String> severities, boolean openOnly, int page, int size);
}
