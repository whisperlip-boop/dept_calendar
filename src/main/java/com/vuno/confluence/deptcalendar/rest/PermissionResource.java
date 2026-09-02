package com.vuno.confluence.deptcalendar.rest;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.vuno.confluence.deptcalendar.rest.dto.PermissionDto;
import com.vuno.confluence.deptcalendar.service.SpaceAccessGuard;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;

/**
 * Lets the calendar UI know up front what the viewer may do, so read-only users never see
 * an Add Event button that would only fail with a 403 once clicked.
 */
@Path("/permission")
public class PermissionResource
{
    private final SpaceAccessGuard accessGuard;

    @Inject
    public PermissionResource(SpaceAccessGuard accessGuard)
    {
        this.accessGuard = accessGuard;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@QueryParam("spaceKey") String spaceKey)
    {
        if (spaceKey == null || spaceKey.isEmpty())
        {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Collections.singletonMap("error", "spaceKey is required"))
                    .build();
        }

        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        PermissionDto dto = new PermissionDto();
        dto.setCanView(accessGuard.canView(user, spaceKey));
        dto.setCanEdit(accessGuard.canEdit(user, spaceKey));
        return Response.ok(dto).build();
    }
}
