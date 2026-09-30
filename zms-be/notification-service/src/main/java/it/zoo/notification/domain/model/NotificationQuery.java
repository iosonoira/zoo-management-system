package it.zoo.notification.domain.model;

import it.zoo.notification.domain.enums.Severity;

import java.util.Set;
import java.util.UUID;

public record NotificationQuery(UUID animalId, Set<Severity> severities, boolean openOnly) {}
