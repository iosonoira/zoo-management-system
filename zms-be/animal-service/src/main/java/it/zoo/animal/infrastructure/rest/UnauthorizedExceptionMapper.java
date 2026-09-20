package it.zoo.animal.infrastructure.rest;

import io.quarkus.security.UnauthorizedException;
import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Overrides the built-in Quarkus mapper, which answers 401 with an empty body.
 */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(UnauthorizedException exception) {
        return Response.status(401)
                .entity(new ErrorResponse("Authentication required"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
