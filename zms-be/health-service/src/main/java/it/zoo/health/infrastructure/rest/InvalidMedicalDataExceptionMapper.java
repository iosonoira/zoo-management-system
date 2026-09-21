package it.zoo.health.infrastructure.rest;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidMedicalDataExceptionMapper implements ExceptionMapper<InvalidMedicalDataException> {

    @Override
    public Response toResponse(InvalidMedicalDataException exception) {
        return Response.status(400)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
