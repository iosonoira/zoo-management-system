package it.zoo.feeding.infrastructure.rest;

import it.zoo.feeding.domain.exception.AnimalDeceasedException;
import it.zoo.feeding.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AnimalDeceasedExceptionMapper implements ExceptionMapper<AnimalDeceasedException> {

    @Override
    public Response toResponse(AnimalDeceasedException exception) {
        return Response.status(422)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
