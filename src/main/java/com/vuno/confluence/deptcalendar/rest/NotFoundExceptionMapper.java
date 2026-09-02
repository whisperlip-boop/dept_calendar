package com.vuno.confluence.deptcalendar.rest;

import com.vuno.confluence.deptcalendar.service.NotFoundException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException>
{
    @Override
    public Response toResponse(NotFoundException exception)
    {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(Collections.singletonMap("error", exception.getMessage()))
                .build();
    }
}
