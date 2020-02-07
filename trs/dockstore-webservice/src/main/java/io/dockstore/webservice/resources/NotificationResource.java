package io.dockstore.webservice.resources;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Notification;
import io.dockstore.webservice.jdbi.NotificationDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

@Path("/curation")
@Api("/curation")
@Produces(MediaType.APPLICATION_JSON)
@io.swagger.v3.oas.annotations.tags.Tag(name = "curation", description = ResourceConstants.CURATION)
public class NotificationResource {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationResource.class);

    // interface between the endpoint and the database
    private final NotificationDAO notificationDAO;

    // constructor
    public NotificationResource(SessionFactory sessionFactory) {
        this.notificationDAO = new NotificationDAO(sessionFactory);
    }

    // get a notification by its id
    @GET
    @Path("/notifications/{id}")
    @UnitOfWork
    @ApiOperation(value = "Return the notification with given id", notes = "NO Authentication", responseContainer = "List", response = Notification.class)
    public Notification getNotification(@PathParam("id") Long id) {
        Notification notification = notificationDAO.findById(id);
        throwErrorIfNull(notification);
        return notification;
    }

    // get all active notifications
    @GET
    @Path("/notifications")
    @UnitOfWork
    @ApiOperation(value = "Return all active notifications", notes = "NO Authentication", responseContainer = "List", response = Notification.class)
    public List<Notification> getActiveNotifications() {
        return notificationDAO.getActiveNotifications();
    }

    // post a new notification
    @POST
    @Path("/notifications")
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @ApiOperation(value = "Create a notification", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)},
            notes = "Curator/admin only", response = Notification.class)
    public Notification createNotification(@ApiParam(value = "Notification to create", required = true) Notification notification) {
        long id = notificationDAO.create(notification);
        return notificationDAO.findById(id);
    }

    // delete a notification by its id
    @DELETE
    @Path("/notifications/{id}")
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "Delete a notification", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Curator/admin only")
    public void deleteNotification(@ApiParam(value = "Notification to delete", required = true) @PathParam("id") Long id) {
        Notification notification = notificationDAO.findById(id);
        throwErrorIfNull(notification);
        notificationDAO.delete(notification);
    }

    @PUT
    @Path("/notifications/{id}")
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "Update a notification", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)},
            notes = "Curator/admin only", response = Notification.class)
    public Notification updateNotification(@ApiParam(value = "Notification to update", required = true) @PathParam("id") long id,
                                           @ApiParam(value = "Updated version of notification", required = true) Notification notification) {
        if (id != notification.getId()) {
            String msg = "ID in path and notification param must match";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        return notificationDAO.update(notification);
    }

    private void throwErrorIfNull(Notification notification) {
        if (notification == null) {
            String msg = "Notification not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
    }

}
