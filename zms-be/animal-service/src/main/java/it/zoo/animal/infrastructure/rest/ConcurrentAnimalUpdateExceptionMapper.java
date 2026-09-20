package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.exception.ConcurrentAnimalUpdateException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConcurrentAnimalUpdateExceptionMapper implements ExceptionMapper<ConcurrentAnimalUpdateException> {

    @Override
    public Response toResponse(ConcurrentAnimalUpdateException exception) {
        return Response.status(409)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
