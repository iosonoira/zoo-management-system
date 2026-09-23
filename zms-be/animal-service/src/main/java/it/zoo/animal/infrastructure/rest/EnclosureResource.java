package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.port.in.ListEnclosuresUseCase;
import it.zoo.animal.infrastructure.rest.dto.EnclosureResponse;
import it.zoo.animal.infrastructure.rest.mapper.EnclosureDtoMapper;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

@ApplicationScoped
@Path("/enclosures")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class EnclosureResource {

    private final ListEnclosuresUseCase listEnclosures;
    private final EnclosureDtoMapper mapper;

    public EnclosureResource(ListEnclosuresUseCase listEnclosures, EnclosureDtoMapper mapper) {
        this.listEnclosures = listEnclosures;
        this.mapper = mapper;
    }

    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public List<EnclosureResponse> list() {
        return mapper.toResponseList(listEnclosures.listAll());
    }
}
