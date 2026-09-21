package it.zoo.health.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.health.domain.port.in.UpdateTreatmentStatusUseCase;
import it.zoo.health.infrastructure.rest.dto.TreatmentResponse;
import it.zoo.health.infrastructure.rest.dto.UpdateTreatmentStatusRequest;
import it.zoo.health.infrastructure.rest.mapper.HealthDtoMapper;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.UUID;

@ApplicationScoped
@Path("/treatments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class TreatmentResource {

    private final UpdateTreatmentStatusUseCase updateTreatmentStatus;
    private final HealthDtoMapper mapper;
    private final SecurityIdentity identity;

    public TreatmentResource(UpdateTreatmentStatusUseCase updateTreatmentStatus,
                             HealthDtoMapper mapper,
                             SecurityIdentity identity) {
        this.updateTreatmentStatus = updateTreatmentStatus;
        this.mapper = mapper;
        this.identity = identity;
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public TreatmentResponse updateStatus(@PathParam("id") UUID id,
                                          @Valid UpdateTreatmentStatusRequest request) {
        return mapper.toResponse(
                updateTreatmentStatus.updateStatus(id, request.status(), currentActor()));
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
