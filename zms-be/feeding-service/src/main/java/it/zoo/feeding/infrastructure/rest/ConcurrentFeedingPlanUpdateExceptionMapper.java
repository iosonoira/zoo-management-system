package it.zoo.feeding.infrastructure.rest;

import it.zoo.feeding.domain.exception.ConcurrentFeedingPlanUpdateException;
import it.zoo.feeding.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConcurrentFeedingPlanUpdateExceptionMapper implements ExceptionMapper<ConcurrentFeedingPlanUpdateException> {

    @Override
    public Response toResponse(ConcurrentFeedingPlanUpdateException exception) {
        return Response.status(409)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
