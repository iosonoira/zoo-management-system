package it.zoo.notification.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.notification.domain.model.NotificationPage;
import it.zoo.notification.domain.port.in.AcknowledgeNotificationUseCase;
import it.zoo.notification.domain.port.in.ListNotificationsUseCase;
import it.zoo.notification.infrastructure.rest.dto.NotificationPageResponse;
import it.zoo.notification.infrastructure.rest.dto.NotificationResponse;
import it.zoo.notification.infrastructure.rest.mapper.NotificationDtoMapper;
import it.zoo.notification.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class NotificationResource {

    private final ListNotificationsUseCase listNotifications;
    private final AcknowledgeNotificationUseCase acknowledgeNotification;
    private final NotificationDtoMapper mapper;
    private final SecurityIdentity identity;

    public NotificationResource(ListNotificationsUseCase listNotifications,
                                AcknowledgeNotificationUseCase acknowledgeNotification,
                                NotificationDtoMapper mapper,
                                SecurityIdentity identity) {
        this.listNotifications = listNotifications;
        this.acknowledgeNotification = acknowledgeNotification;
        this.mapper = mapper;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public NotificationPageResponse list(@QueryParam("animalId") UUID animalId,
                                         @QueryParam("severity") List<String> severity,
                                         @QueryParam("open") @DefaultValue("false") boolean open,
                                         @QueryParam("page") @DefaultValue("0") int page,
                                         @QueryParam("size") @DefaultValue("20") int size) {
        NotificationPage result = listNotifications.list(animalId, severity, open, page, size);
        return new NotificationPageResponse(
                mapper.toResponseList(result.items()),
                result.page(),
                result.size(),
                result.total()
        );
    }

    @PUT
    @Path("/{id}/acknowledge")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public NotificationResponse acknowledge(@PathParam("id") UUID id) {
        return mapper.toResponse(acknowledgeNotification.acknowledge(id, currentActor()));
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
