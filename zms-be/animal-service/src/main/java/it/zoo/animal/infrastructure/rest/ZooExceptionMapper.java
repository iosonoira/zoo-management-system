package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ZooExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof AnimalNotFoundException) {
            return errorResponse(404, exception.getMessage());
        }
        if (exception instanceof InvalidAnimalDataException) {
            return errorResponse(400, exception.getMessage());
        }
        if (exception instanceof InvalidStatusTransitionException) {
            return errorResponse(422, exception.getMessage());
        }
        return errorResponse(500, "Internal server error");
    }

    private Response errorResponse(int status, String message) {
        return Response.status(status)
                .entity(new ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    public record ErrorResponse(String message) {}
}
