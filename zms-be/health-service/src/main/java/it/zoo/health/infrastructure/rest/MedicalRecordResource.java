package it.zoo.health.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordDetail;
import it.zoo.health.domain.model.MedicalRecordPage;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.*;
import it.zoo.health.infrastructure.rest.dto.*;
import it.zoo.health.infrastructure.rest.mapper.HealthDtoMapper;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.UUID;

@ApplicationScoped
@Path("/medical-records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class MedicalRecordResource {

    private final CreateMedicalRecordUseCase createMedicalRecord;
    private final GetMedicalRecordUseCase getMedicalRecord;
    private final ListMedicalRecordsUseCase listMedicalRecords;
    private final PrescribeTreatmentUseCase prescribeTreatment;
    private final HealthDtoMapper mapper;
    private final SecurityIdentity identity;

    public MedicalRecordResource(CreateMedicalRecordUseCase createMedicalRecord,
                                 GetMedicalRecordUseCase getMedicalRecord,
                                 ListMedicalRecordsUseCase listMedicalRecords,
                                 PrescribeTreatmentUseCase prescribeTreatment,
                                 HealthDtoMapper mapper,
                                 SecurityIdentity identity) {
        this.createMedicalRecord = createMedicalRecord;
        this.getMedicalRecord = getMedicalRecord;
        this.listMedicalRecords = listMedicalRecords;
        this.prescribeTreatment = prescribeTreatment;
        this.mapper = mapper;
        this.identity = identity;
    }

    @POST
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public Response create(@Valid CreateMedicalRecordRequest request) {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                request.animalId(), request.reason(), request.diagnosis(),
                request.examinedOn(), request.veterinarian(), currentActor()
        );
        MedicalRecord record = createMedicalRecord.create(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(record))
                .build();
    }

    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public MedicalRecordPageResponse list(@QueryParam("animalId") UUID animalId,
                                          @QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("20") int size) {
        MedicalRecordPage result = listMedicalRecords.list(animalId, page, size);
        return new MedicalRecordPageResponse(
                mapper.toResponseList(result.items()),
                result.page(),
                result.size(),
                result.total()
        );
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public MedicalRecordDetailResponse getById(@PathParam("id") UUID id) {
        MedicalRecordDetail detail = getMedicalRecord.getById(id);
        MedicalRecord record = detail.record();
        return new MedicalRecordDetailResponse(
                record.getId(),
                record.getAnimalId(),
                record.getReason(),
                record.getDiagnosis(),
                record.getExaminedOn(),
                record.getVeterinarian(),
                record.getCreatedBy(),
                record.getUpdatedBy(),
                mapper.toTreatmentResponseList(detail.treatments())
        );
    }

    @POST
    @Path("/{id}/treatments")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public Response prescribe(@PathParam("id") UUID id,
                              @Valid PrescribeTreatmentRequest request) {
        Treatment treatment = prescribeTreatment.prescribe(
                new PrescribeTreatmentCommand(id, request.description(), currentActor()));
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(treatment))
                .build();
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
