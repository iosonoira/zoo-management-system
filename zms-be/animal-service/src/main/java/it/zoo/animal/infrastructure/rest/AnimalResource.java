package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.*;
import it.zoo.animal.infrastructure.rest.dto.AnimalResponse;
import it.zoo.animal.infrastructure.rest.dto.RegisterAnimalRequest;
import it.zoo.animal.infrastructure.rest.dto.TransferAnimalRequest;
import it.zoo.animal.infrastructure.rest.dto.UpdateAnimalStatusRequest;
import it.zoo.animal.infrastructure.rest.mapper.AnimalDtoMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/animals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnimalResource {

    private final RegisterAnimalUseCase registerAnimal;
    private final GetAnimalUseCase getAnimal;
    private final ListAnimalsUseCase listAnimals;
    private final UpdateAnimalStatusUseCase updateAnimalStatus;
    private final TransferAnimalUseCase transferAnimal;
    private final AnimalDtoMapper mapper;

    public AnimalResource(RegisterAnimalUseCase registerAnimal,
                          GetAnimalUseCase getAnimal,
                          ListAnimalsUseCase listAnimals,
                          UpdateAnimalStatusUseCase updateAnimalStatus,
                          TransferAnimalUseCase transferAnimal,
                          AnimalDtoMapper mapper) {
        this.registerAnimal = registerAnimal;
        this.getAnimal = getAnimal;
        this.listAnimals = listAnimals;
        this.updateAnimalStatus = updateAnimalStatus;
        this.transferAnimal = transferAnimal;
        this.mapper = mapper;
    }

    @POST
    public Response register(@Valid RegisterAnimalRequest request) {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                request.name(), request.species(), request.dangerous(),
                request.habitat(), request.enclosureId(), request.arrivalDate()
        );
        Animal animal = registerAnimal.register(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(animal))
                .build();
    }

    @GET
    public List<AnimalResponse> listAll() {
        return mapper.toResponseList(listAnimals.listAll());
    }

    @GET
    @Path("/{id}")
    public AnimalResponse getById(@PathParam("id") UUID id) {
        return mapper.toResponse(getAnimal.getById(id));
    }

    @PUT
    @Path("/{id}/status")
    public AnimalResponse updateStatus(@PathParam("id") UUID id,
                                       @Valid UpdateAnimalStatusRequest request) {
        return mapper.toResponse(updateAnimalStatus.updateStatus(id, request.status()));
    }

    @PUT
    @Path("/{id}/transfer")
    public AnimalResponse transfer(@PathParam("id") UUID id,
                                   @Valid TransferAnimalRequest request) {
        return mapper.toResponse(transferAnimal.transfer(id, request.targetEnclosureId()));
    }
}
