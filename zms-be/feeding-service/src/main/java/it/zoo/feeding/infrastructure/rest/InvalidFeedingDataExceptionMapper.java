package it.zoo.feeding.infrastructure.rest;

import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidFeedingDataExceptionMapper implements ExceptionMapper<InvalidFeedingDataException> {

    @Override
    public Response toResponse(InvalidFeedingDataException exception) {
        return Response.status(400)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
