package it.zoo.animal.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalPage;
import it.zoo.animal.domain.port.in.*;
import it.zoo.animal.infrastructure.rest.dto.AnimalPageResponse;
import it.zoo.animal.infrastructure.rest.dto.AnimalResponse;
import it.zoo.animal.infrastructure.rest.dto.RegisterAnimalRequest;
import it.zoo.animal.infrastructure.rest.dto.TransferAnimalRequest;
import it.zoo.animal.infrastructure.rest.dto.UpdateAnimalStatusRequest;
import it.zoo.animal.infrastructure.rest.mapper.AnimalDtoMapper;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/animals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class AnimalResource {

    private final RegisterAnimalUseCase registerAnimal;
    private final GetAnimalUseCase getAnimal;
    private final ListAnimalsUseCase listAnimals;
    private final UpdateAnimalStatusUseCase updateAnimalStatus;
    private final TransferAnimalUseCase transferAnimal;
    private final AnimalDtoMapper mapper;
    private final SecurityIdentity identity;

    public AnimalResource(RegisterAnimalUseCase registerAnimal,
                          GetAnimalUseCase getAnimal,
                          ListAnimalsUseCase listAnimals,
                          UpdateAnimalStatusUseCase updateAnimalStatus,
                          TransferAnimalUseCase transferAnimal,
                          AnimalDtoMapper mapper,
                          SecurityIdentity identity) {
        this.registerAnimal = registerAnimal;
        this.getAnimal = getAnimal;
        this.listAnimals = listAnimals;
        this.updateAnimalStatus = updateAnimalStatus;
        this.transferAnimal = transferAnimal;
        this.mapper = mapper;
        this.identity = identity;
    }

    @POST
    @RolesAllowed(ZooRoles.ADMIN)
    public Response register(@Valid RegisterAnimalRequest request) {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                request.name(), request.species(), request.dangerous(),
                request.habitat(), request.enclosureId(), request.arrivalDate(),
                currentActor()
        );
        Animal animal = registerAnimal.register(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(animal))
                .build();
    }

    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public AnimalPageResponse list(@QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("size") @DefaultValue("20") int size) {
        AnimalPage result = listAnimals.list(page, size);
        return new AnimalPageResponse(
                mapper.toResponseList(result.items()),
                result.page(),
                result.size(),
                result.total()
        );
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public AnimalResponse getById(@PathParam("id") UUID id) {
        return mapper.toResponse(getAnimal.getById(id));
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public AnimalResponse updateStatus(@PathParam("id") UUID id,
                                       @Valid UpdateAnimalStatusRequest request) {
        return mapper.toResponse(updateAnimalStatus.updateStatus(id, request.status(), currentActor()));
    }

    @PUT
    @Path("/{id}/transfer")
    @RolesAllowed({ZooRoles.KEEPER, ZooRoles.ADMIN})
    public AnimalResponse transfer(@PathParam("id") UUID id,
                                   @Valid TransferAnimalRequest request) {
        return mapper.toResponse(transferAnimal.transfer(id, request.targetEnclosureId(), currentActor()));
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
