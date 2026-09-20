package it.zoo.animal.infrastructure.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * An unparseable body is a client error; without this it reaches the catch-all
 * mapper and answers 500. The parser message is not echoed back.
 */
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(JsonProcessingException exception) {
        return Response.status(400)
                .entity(new ErrorResponse("Malformed request body"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
