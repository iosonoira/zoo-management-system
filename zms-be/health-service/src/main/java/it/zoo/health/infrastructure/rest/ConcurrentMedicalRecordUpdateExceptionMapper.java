package it.zoo.health.infrastructure.rest;

import it.zoo.health.domain.exception.ConcurrentMedicalRecordUpdateException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConcurrentMedicalRecordUpdateExceptionMapper implements ExceptionMapper<ConcurrentMedicalRecordUpdateException> {

    @Override
    public Response toResponse(ConcurrentMedicalRecordUpdateException exception) {
        return Response.status(409)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
