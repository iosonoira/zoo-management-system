package it.zoo.health.infrastructure.rest;

import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Last resort. Handles unexpected runtime exceptions. For WebApplicationException
 * (which includes framework-level errors like path parameter parsing), we return
 * its response directly to preserve proper HTTP status codes (400/404).
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException webAppEx) {
            return webAppEx.getResponse();
        }
        LOG.error("Unhandled exception while serving request", exception);
        return Response.status(500)
                .entity(new ErrorResponse("Internal server error"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
