package it.zoo.notification.infrastructure.rest.dto;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long total
) {}
