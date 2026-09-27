package it.zoo.feeding.infrastructure.rest;

import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FeedingPlanNotFoundExceptionMapper implements ExceptionMapper<FeedingPlanNotFoundException> {

    @Override
    public Response toResponse(FeedingPlanNotFoundException exception) {
        return Response.status(404)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
