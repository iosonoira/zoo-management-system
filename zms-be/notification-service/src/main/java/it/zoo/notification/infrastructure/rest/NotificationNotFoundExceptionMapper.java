package it.zoo.notification.infrastructure.rest;

import it.zoo.notification.domain.exception.NotificationNotFoundException;
import it.zoo.notification.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotificationNotFoundExceptionMapper implements ExceptionMapper<NotificationNotFoundException> {

    @Override
    public Response toResponse(NotificationNotFoundException exception) {
        return Response.status(404)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
