package it.zoo.health.infrastructure.rest;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {

    @Override
    public Response toResponse(SecurityException exception) {
        if (exception instanceof ForbiddenException) {
            return errorResponse(403, "Insufficient role");
        }
        if (exception instanceof UnauthorizedException
                || exception instanceof AuthenticationFailedException) {
            return errorResponse(401, "Authentication required");
        }
        return errorResponse(403, "Access denied");
    }

    private Response errorResponse(int status, String message) {
        return Response.status(status)
                .entity(new ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
