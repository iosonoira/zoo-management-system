package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidAnimalDataExceptionMapper implements ExceptionMapper<InvalidAnimalDataException> {

    @Override
    public Response toResponse(InvalidAnimalDataException exception) {
        return Response.status(400)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
