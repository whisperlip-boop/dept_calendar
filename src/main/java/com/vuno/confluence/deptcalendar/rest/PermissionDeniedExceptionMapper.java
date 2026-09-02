package com.vuno.confluence.deptcalendar.rest;

import com.vuno.confluence.deptcalendar.service.PermissionDeniedException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;

@Provider
public class PermissionDeniedExceptionMapper implements ExceptionMapper<PermissionDeniedException>
{
    @Override
    public Response toResponse(PermissionDeniedException exception)
    {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity(Collections.singletonMap("error", exception.getMessage()))
                .build();
    }
}
