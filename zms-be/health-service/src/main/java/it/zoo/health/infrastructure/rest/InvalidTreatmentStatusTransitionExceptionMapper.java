package it.zoo.health.infrastructure.rest;

import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidTreatmentStatusTransitionExceptionMapper implements ExceptionMapper<InvalidTreatmentStatusTransitionException> {

    @Override
    public Response toResponse(InvalidTreatmentStatusTransitionException exception) {
        return Response.status(422)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
