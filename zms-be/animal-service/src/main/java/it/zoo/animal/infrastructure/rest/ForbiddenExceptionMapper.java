package it.zoo.animal.infrastructure.rest;

import io.quarkus.security.ForbiddenException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Overrides the built-in Quarkus mapper, which answers 403 with an empty body.
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        return Response.status(403)
                .entity(new ErrorResponse("Insufficient role"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
