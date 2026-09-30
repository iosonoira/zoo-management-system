package it.zoo.notification.domain.model;

import java.util.List;

public record NotificationPage(List<Notification> items, int page, int size, long total) {}
