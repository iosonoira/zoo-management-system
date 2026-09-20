package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AnimalNotFoundExceptionMapper implements ExceptionMapper<AnimalNotFoundException> {

    @Override
    public Response toResponse(AnimalNotFoundException exception) {
        return Response.status(404)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
