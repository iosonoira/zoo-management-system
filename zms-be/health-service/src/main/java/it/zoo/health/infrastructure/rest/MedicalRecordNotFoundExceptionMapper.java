package it.zoo.health.infrastructure.rest;

import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MedicalRecordNotFoundExceptionMapper implements ExceptionMapper<MedicalRecordNotFoundException> {

    @Override
    public Response toResponse(MedicalRecordNotFoundException exception) {
        return Response.status(404)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
