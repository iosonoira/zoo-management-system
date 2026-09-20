package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Last resort. Mapping Exception rather than RuntimeException leaves the
 * framework's own WebApplicationException handling in place, so a malformed
 * path parameter or an unparseable body still answers 400/404 instead of 500.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled exception while serving request", exception);
        return Response.status(500)
                .entity(new ErrorResponse("Internal server error"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
