package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidStatusTransitionExceptionMapper implements ExceptionMapper<InvalidStatusTransitionException> {

    @Override
    public Response toResponse(InvalidStatusTransitionException exception) {
        return Response.status(422)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
