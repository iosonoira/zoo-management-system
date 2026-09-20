package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Keeps the status the framework already chose (404 for an unmatched route or
 * an unconvertible path parameter, 400 for a bad body) and gives it the same
 * JSON body as every other error.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        if (status >= 500) {
            LOG.error("Request failed", exception);
        }
        return Response.status(status)
                .entity(new ErrorResponse(reasonFor(status)))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String reasonFor(int status) {
        return switch (status) {
            case 400 -> "Malformed request";
            case 404 -> "Resource not found";
            case 405 -> "Method not allowed";
            case 415 -> "Unsupported media type";
            default -> status >= 500 ? "Internal server error" : "Request rejected";
        };
    }
}
