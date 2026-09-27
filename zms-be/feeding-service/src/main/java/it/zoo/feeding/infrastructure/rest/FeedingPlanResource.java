package it.zoo.feeding.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPage;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.model.FeedingPlanPage;
import it.zoo.feeding.domain.port.in.*;
import it.zoo.feeding.infrastructure.rest.dto.*;
import it.zoo.feeding.infrastructure.rest.mapper.FeedingDtoMapper;
import it.zoo.feeding.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.UUID;

@ApplicationScoped
@Path("/feeding-plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class FeedingPlanResource {

    private final CreateFeedingPlanUseCase createFeedingPlan;
    private final GetFeedingPlanUseCase getFeedingPlan;
    private final ListFeedingPlansUseCase listFeedingPlans;
    private final UpdateFeedingPlanStatusUseCase updateFeedingPlanStatus;
    private final RecordFeedingUseCase recordFeeding;
    private final ListFeedingsUseCase listFeedings;
    private final FeedingDtoMapper mapper;
    private final SecurityIdentity identity;

    public FeedingPlanResource(CreateFeedingPlanUseCase createFeedingPlan,
                               GetFeedingPlanUseCase getFeedingPlan,
                               ListFeedingPlansUseCase listFeedingPlans,
                               UpdateFeedingPlanStatusUseCase updateFeedingPlanStatus,
                               RecordFeedingUseCase recordFeeding,
                               ListFeedingsUseCase listFeedings,
                               FeedingDtoMapper mapper,
                               SecurityIdentity identity) {
        this.createFeedingPlan = createFeedingPlan;
        this.getFeedingPlan = getFeedingPlan;
        this.listFeedingPlans = listFeedingPlans;
        this.updateFeedingPlanStatus = updateFeedingPlanStatus;
        this.recordFeeding = recordFeeding;
        this.listFeedings = listFeedings;
        this.mapper = mapper;
        this.identity = identity;
    }

    @POST
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public Response create(@Valid CreateFeedingPlanRequest request) {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                request.animalId(), request.food(), request.quantityGrams(),
                request.feedingTimes(), request.notes(), currentActor()
        );
        FeedingPlan plan = createFeedingPlan.create(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(plan))
                .build();
    }

    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public FeedingPlanPageResponse list(@QueryParam("animalId") UUID animalId,
                                        @QueryParam("page") @DefaultValue("0") int page,
                                        @QueryParam("size") @DefaultValue("20") int size) {
        FeedingPlanPage result = listFeedingPlans.list(animalId, page, size);
        return new FeedingPlanPageResponse(
                mapper.toResponseList(result.items()),
                result.page(),
                result.size(),
                result.total()
        );
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public FeedingPlanResponse getById(@PathParam("id") UUID id) {
        return mapper.toResponse(getFeedingPlan.getById(id));
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public FeedingPlanResponse updateStatus(@PathParam("id") UUID id,
                                            @Valid UpdateFeedingPlanStatusRequest request) {
        FeedingPlan plan = updateFeedingPlanStatus.updateStatus(id, request.status(), currentActor());
        return mapper.toResponse(plan);
    }

    @POST
    @Path("/{id}/feedings")
    @RolesAllowed({ZooRoles.KEEPER, ZooRoles.ADMIN})
    public Response recordFeeding(@PathParam("id") UUID id,
                                  @Valid RecordFeedingRequest request) {
        RecordFeedingCommand cmd = new RecordFeedingCommand(
                id, request.fedAt(), request.quantityGrams(), request.notes(), currentActor()
        );
        Feeding feeding = recordFeeding.record(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(feeding))
                .build();
    }

    @GET
    @Path("/{id}/feedings")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public FeedingPageResponse listFeedings(@PathParam("id") UUID id,
                                            @QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("20") int size) {
        FeedingPage result = listFeedings.list(id, page, size);
        return new FeedingPageResponse(
                mapper.toFeedingResponseList(result.items()),
                result.page(),
                result.size(),
                result.total()
        );
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
