package it.zoo.health.infrastructure.rest;

import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TreatmentNotFoundExceptionMapper implements ExceptionMapper<TreatmentNotFoundException> {

    @Override
    public Response toResponse(TreatmentNotFoundException exception) {
        return Response.status(404)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
