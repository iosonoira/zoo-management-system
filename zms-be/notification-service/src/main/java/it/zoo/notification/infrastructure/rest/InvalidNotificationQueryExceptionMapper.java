package it.zoo.notification.infrastructure.rest;

import it.zoo.notification.domain.exception.InvalidNotificationQueryException;
import it.zoo.notification.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidNotificationQueryExceptionMapper implements ExceptionMapper<InvalidNotificationQueryException> {

    @Override
    public Response toResponse(InvalidNotificationQueryException exception) {
        return Response.status(400)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
