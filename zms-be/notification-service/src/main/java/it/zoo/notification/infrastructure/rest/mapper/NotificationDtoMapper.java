package it.zoo.notification.infrastructure.rest.mapper;

import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.infrastructure.rest.dto.NotificationResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface NotificationDtoMapper {
    NotificationResponse toResponse(Notification notification);
    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
